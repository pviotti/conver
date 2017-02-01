package conver.db

import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.core.command.ExecStartResultCallback
import com.github.dockerjava.core.command.AttachContainerResultCallback
import com.github.dockerjava.api.model.Frame
import java.util.LinkedList
import scala.collection.mutable.ListBuffer

object ZkCluster extends Cluster {

  val netName = "zk"

  def start(num: Int): Array[String] = {

    // TODO check and handle ConflictException in case network
    // or containers are already there

    //val info = docker.infoCmd().exec()
    //println(docker.infoCmd().exec())

    var containers = Array.ofDim[String](num)
    val network = docker.createNetworkCmd().withName(netName).exec()

    val sb = StringBuilder.newBuilder
    sb.append("SERVERS=")
    for (i <- 1 to num)
      if (i < num) sb.append("zookeeper" + i + ",")
      else sb.append("zookeeper" + i)
    val servers = sb.toString

    for (i <- 1 to num) {
      val container = docker.createContainerCmd("pviotti/zookeeper:3.4.9")
        .withName("zookeeper" + i)
        .withHostName("zookeeper" + i)
        .withEnv("MYID=" + i, servers)
        .withNetworkMode(netName)
        .exec()
      docker.startContainerCmd(container.getId).exec()

      val netInfo = docker.inspectNetworkCmd().withNetworkId(network.getId).exec()
      val ipAddr = netInfo.getContainers.get(container.getId).getIpv4Address
      println("Server zk" + i + " started: " + ipAddr)

      containers(i - 1) = container.getId
    }

    containers
  }

  def getConnectionString(cIds: Array[String]): String = {
    val sb = StringBuilder.newBuilder
    for (i <- 0 until cIds.length) {
      val netInfo = docker.inspectNetworkCmd().withNetworkId(netName).exec()
      var ipAddr = netInfo.getContainers.get(cIds(i)).getIpv4Address
      if (i != cIds.length - 1)
        sb.append(ipAddr.substring(0, ipAddr.lastIndexOf('/')) + ":2181,")
      else
        sb.append(ipAddr.substring(0, ipAddr.lastIndexOf('/')) + ":2181")
    }
    sb.toString()
  }

  def stop(cIds: Array[String]) = {
    for (cId <- cIds) {
      docker.stopContainerCmd(cId).exec()
      val statusCode = docker.waitContainerCmd(cId)
        .exec(new WaitContainerResultCallback)
        .awaitStatusCode()

      if (statusCode == 143) { // SIGTERM
        docker.removeContainerCmd(cId).exec()
        //println("Container " + cId.substring(0, 5) + " successfully terminated")
      }
    }
    docker.removeNetworkCmd(netName).exec()
  }

  def printState(cIds: Array[String]) = {
    for (cId <- cIds) {
      val inspect = docker.inspectContainerCmd(cId).exec()
      println(inspect)
    }
  }

  //  def main(arg: Array[String]): Unit = {
  //      var contIds = start(3)
  //      slowDownNetwork(contIds)
  //      //printState(contIds)
  //      Thread.sleep(2000)
  //      getConnectionString(contIds)
  //      stop(contIds)
  //  }

}
