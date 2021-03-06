package conver.db

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.AttachContainerResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.core.command.PullImageResultCallback
import com.typesafe.scalalogging.LazyLogging
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.BadRequestException

abstract class Cluster extends LazyLogging {

  val docker = DockerClientBuilder.getInstance().build()
  val iptablesDockerImage = "vimagick/iptables:latest"

  // netem paramters ( https://wiki.linuxfoundation.org/networking/netem )
  val meanDelay = 100 // ms - mean delay
  val varDelay = 20 // ms - random delay variation
  val corrupt = 2 // %  - packet corruption
  val loss = 5 // %  - packet loss
  val dup = 2 // %  - packet duplication

  def start(num: Int): Array[String]

  def stop(cIds: Array[String]) = {
    try {
      for (cId <- cIds)
        docker.removeContainerCmd(cId).withForce(true).exec()
    } catch {
      case e: NotFoundException => ; // container already removed
      case e: BadRequestException => ; // removal already in progress 
    }
  }

  def slowDownNetwork(cIds: Array[String]) = {

    // get containers' interface number
    var ifLst = new ListBuffer[String]()
    for (c <- cIds) {
      val cb = new StringContainerResultCallback()
      val cmd = docker
        .execCreateCmd(c)
        .withCmd("ip", "link", "show", "eth0")
        .withAttachStdout(true)
        .exec()
      docker
        .execStartCmd(cmd.getId)
        .withDetach(false)
        .withTty(true)
        .exec(cb)
        .awaitCompletion()
      val ifnum = cb.toString().split(":")(0)
      ifLst += (ifnum.toInt + 1).toString // usually, host's interface n. == container interface n. + 1
    }

    pullDockerImage(iptablesDockerImage)

    // execute "ip link" to get containers' interface name on the host
    val cb1 = new StringContainerResultCallback()
    val iptableContainer = docker
      .createContainerCmd(iptablesDockerImage)
      .withCmd("ip", "link")
      .withNetworkMode("host") // to see other containers' devices
      .withPrivileged(true)
      .exec()
    docker.startContainerCmd(iptableContainer.getId).exec()
    docker
      .attachContainerCmd(iptableContainer.getId)
      .withStdErr(true)
      .withStdOut(true)
      .withFollowStream(true)
      .withLogs(true)
      .exec(cb1)
      .awaitCompletion()
    docker.removeContainerCmd(iptableContainer.getId).withForce(true).exec()
    val iplinkstr = cb1.toString()
    //println(iplinkstr)

    // apply filter on each host interface that maps to a container interface
    for (ifc <- ifLst) {
      val pat = s"$ifc: ([^:@]+)[:@]".r // e.g. "554: veth25b990c@"
      val ifh = pat.findFirstIn(iplinkstr).get.split(" ")(1).split("@")(0) // e.g. "veth25b990c"

      val cb2 = new StringContainerResultCallback()
      val iptableContainer = docker
        .createContainerCmd(iptablesDockerImage)
        .withCmd("tc", "qdisc", "replace", "dev", ifh, "root", "netem",
          "delay", s"${meanDelay}ms", s"${varDelay}ms", "distribution", "normal",
          "corrupt", s"${corrupt}%", "loss", s"${loss}%", "duplicate", s"${dup}%")
        .withNetworkMode("host") // to see other containers' devices
        .withPrivileged(true)
        .exec()
      docker.startContainerCmd(iptableContainer.getId).exec()
      docker.removeContainerCmd(iptableContainer.getId).withForce(true).exec()
    }
  }

  protected def pullDockerImage(imageId: String) = {
    val images = docker.listImagesCmd().exec();
    if (!images.exists { x =>
      x.getRepoTags.head.equals(imageId)
    }) {
      logger.info(s"Pulling docker image $imageId...")
      docker
        .pullImageCmd(imageId)
        .exec(new PullImageResultCallback())
        .awaitSuccess();
    }
  }
}

class StringContainerResultCallback extends AttachContainerResultCallback {
  val sb = new StringBuffer
  override def onNext(item: Frame) {
    sb.append(new String(item.getPayload))
    super.onNext(item)
  }
  override def toString() = sb.toString()
}
