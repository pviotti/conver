package conver.db

import scala.collection.mutable.ListBuffer
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.AttachContainerResultCallback
import com.github.dockerjava.api.model.Frame

abstract class Cluster {

  def start(num: Int): Array[String]
  def stop(cIds: Array[String])

  val docker = DockerClientBuilder.getInstance().build()

  def slowDownNetwork(cIds: Array[String]) = {

    // get containers' interface number
    var ifLst = new ListBuffer[String]()
    for (c <- cIds) {
      val cb = new StringContainerResultCallback()
      val cmd = docker.execCreateCmd(c).withCmd("ip", "link", "show", "eth0").withAttachStdout(true).exec()
      docker.execStartCmd(cmd.getId).withDetach(false).withTty(true).exec(cb).awaitCompletion()
      val ifnum = cb.toString().split(":")(0)
      ifLst += (ifnum.toInt + 1).toString // usually, host's interface n. == container interface n. + 1
    }

    // execute "ip link" to get containers' interface name on the host
    val cb1 = new StringContainerResultCallback()
    val iptableContainer = docker.createContainerCmd("vimagick/iptables:latest")
      .withCmd("ip", "link")
      .withNetworkMode("host") // important to see other containers' devices
      .withPrivileged(true)
      .exec()
    docker.startContainerCmd(iptableContainer.getId()).exec()
    docker.attachContainerCmd(iptableContainer.getId)
      .withStdErr(true).withStdOut(true)
      .withFollowStream(true).withLogs(true)
      .exec(cb1).awaitCompletion()
    val iplinkstr = cb1.toString()
    //println(iplinkstr)

    // apply filter on each host interface that maps to a container interface
    for (ifc <- ifLst) {
      val pat = s"$ifc: ([^:@]+)[:@]".r // e.g. "554: veth25b990c@"
      val ifh = pat.findFirstIn(iplinkstr).get.split(" ")(1).split("@")(0) // e.g. "veth25b990c"

      val cb2 = new StringContainerResultCallback()
      val iptableContainer = docker.createContainerCmd("vimagick/iptables:latest")
        .withCmd("tc", "qdisc", "replace", "dev", ifh, "root", "netem", "delay", "75ms", "100ms", "distribution", "normal")
        .withNetworkMode("host") // important to see other containers' devices
        .withPrivileged(true)
        .exec()
      docker.startContainerCmd(iptableContainer.getId()).exec()
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