package conver.db

import java.nio.file.Files
import java.nio.file.Path

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Link
import com.github.dockerjava.core.command.WaitContainerResultCallback
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import com.github.dockerjava.core.command.BuildImageResultCallback
import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.core.command.ExecStartResultCallback
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Volume
import java.util.LinkedList
import com.typesafe.scalalogging.LazyLogging

object AntidoteDBCluster extends Cluster with LazyLogging {

  val antidoteDockerImage = "mweber/antidotedb:latest"
  val erlangDockerImage = "erlang:19-slim"

  // Bash script to call Erlang scripts that connects Antidote containers
  // as in https://github.com/mweberUKL/antidote_dev/tree/master/docker_dcs
  val scriptLink = "#!/bin/bash \nsleep 8; \nescript /code/connect_dcs.erl"

  // Erlang script template to connect Antidote containers
  val scriptHeader =
    "#!/usr/bin/env escript \n%%! -smp enable -sname erlshell -setcookie antidote \nmain(_Args) -> \n"
  val scriptStartInterDcMgr =
    "\trpc:call(antidote@antidoteID, inter_dc_manager, start_bg_processes, [stable]),\n"
  val scriptGetInterDcMgrDescr =
    "\t{ok, DescID} = rpc:call(antidote@antidoteID, inter_dc_manager, get_descriptor, []),\n"
  val scriptSyncInterDcMgr =
    "\trpc:call(antidote@antidoteID, inter_dc_manager, observe_dcs_sync, [Descriptors]),\n"

  def start(num: Int): Array[String] = {

    // TODO check and handle ConflictException in case network
    // or containers are already there

    pullDockerImage(antidoteDockerImage)
    pullDockerImage(erlangDockerImage)

    var containers = Array.ofDim[String](num)

    for (i <- 1 to num) {
      val container = docker
        .createContainerCmd(antidoteDockerImage)
        .withName("antidote" + i)
        .withHostName("antidote" + i)
        .withEnv("NODE_NAME=antidote@antidote" + i, "SHORT_NAME=true")
        .withPortBindings(PortBinding.parse((8086 + i).toString + ":8087"))
        .exec()
      docker.startContainerCmd(container.getId).exec()
      logger.info("Server antidote" + i + " started")

      containers(i - 1) = container.getId
    }

    val (scriptDir, tmpScriptFile, tmpEscriptFile) = createScriptFiles(num)

    val cb = new StringContainerResultCallback()
    val lstLinks = new LinkedList[Link]
    for (i <- 1 to num)
      lstLinks.add(new Link("antidote" + i, "antidote" + i + "link"))
    val linkContainer = docker
      .createContainerCmd(erlangDockerImage)
      .withName("link")
      .withHostName("link")
      .withBinds(new Bind(scriptDir.toString, new Volume("/code")))
      .withLinks(lstLinks)
      .withCmd("/code/link.sh")
      .exec()
    docker.startContainerCmd(linkContainer.getId()).exec()
    docker
      .attachContainerCmd(linkContainer.getId)
      .withStdErr(true)
      .withStdOut(true)
      .withFollowStream(true)
      .withLogs(true)
      .exec(cb)
      .awaitCompletion()
    logger.info(cb.toString())
    docker.removeContainerCmd(linkContainer.getId).exec()
    Files.deleteIfExists(tmpScriptFile)
    Files.deleteIfExists(tmpEscriptFile)
    Files.deleteIfExists(scriptDir)

    containers
  }

  def getConnectionString(num: Int) = {
    val sb = StringBuilder.newBuilder
    for (i <- 1 to num)
      if (i != num) sb.append((8086 + i).toString + ",")
      else sb.append((8086 + i).toString)
    sb.toString
  }

  private def createScriptFiles(num: Int) = {
    val tmpDir = Files.createTempDirectory(null)
    val tmpScriptFile = Files.createFile(
      Paths.get(tmpDir.toString(), "link.sh"),
      PosixFilePermissions.asFileAttribute(
        PosixFilePermissions.fromString("rwxr-xr-x")))
    val tmpEscriptFile = Files.createFile(
      Paths.get(tmpDir.toString(), "connect_dcs.erl"),
      PosixFilePermissions.asFileAttribute(
        PosixFilePermissions.fromString("rwxr-xr-x")))

    // write shell script file
    var writer = Files.newBufferedWriter(tmpScriptFile)
    writer.write(scriptLink)
    writer.close()

    // write Erlang script file
    writer = Files.newBufferedWriter(tmpEscriptFile)
    writer.write(scriptHeader)
    for (i <- 1 to num)
      writer.write(scriptStartInterDcMgr.replaceAll("ID", i.toString))
    for (i <- 1 to num)
      writer.write(scriptGetInterDcMgrDescr.replaceAll("ID", i.toString))
    val sb = StringBuilder.newBuilder
    sb.append("\tDescriptors = [")
    for (i <- 1 to num)
      if (i != num) sb.append("Desc" + i + ",")
      else sb.append("Desc" + i + "],\n")
    writer.write(sb.toString())
    for (i <- 1 to num)
      writer.write(scriptSyncInterDcMgr.replaceAll("ID", i.toString))

    writer.write("\tio:format(\"Antidote cluster setup completed.\").")
    writer.close()

    (tmpDir, tmpScriptFile, tmpEscriptFile)
  }

}
