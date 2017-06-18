package conver

import conver.clients.DummyLinClient
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import conver.clients.Client
import scala.collection.mutable.HashSet
import conver.clients.DummyRegClient
import conver.clients.ZkClient
import conver.db.ZkCluster
import java.util.concurrent.Executors
import com.github.dockerjava.api.exception.ConflictException
import conver.clients.AntidoteDBClient
import conver.clients.AntidoteDBClient
import conver.db.AntidoteDBCluster
import conver.db.Cluster
import org.rogach.scallop._

class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {

  banner(
    """Usage: conver [OPTIONS]
           |Conver spawns clusters of databases and perform concurrent read and write operations. 
           |At the end of each execution, it verify that some consistency semantics were respected.
           |Options:
           |""".stripMargin)

  val database = opt[String](default = Some("lin"),
    short = 'd',
    descr = "Database (lin, reg, zk, antidote)")
  val numServers =
    opt[Int](default = Some(3), short = 's', descr = "Number of servers")
  val numClients =
    opt[Int](default = Some(3), short = 'c', descr = "Number of clients")
  val meanNumOps = opt[Int](default = Some(10),
    short = 'o',
    descr = "Average number of operations per client")
  val wan = opt[Boolean](descr = "Emulate wide area network")

  verify()
}

object Conver extends App {

  val conf = new Conf(args)
  val database = conf.database()
  val numServers = conf.numServers()
  val numClients = conf.numClients()
  val meanNumOp = conf.meanNumOps()
  val wan = conf.wan()
  val sigmaNumOp = 1
  val maxInterOpInterval = if (wan) ZkCluster.meanDelay else 20
  val readRatio = 50 // %
  println(
    s"Started. Database: $database, servers: $numServers," +
      s"clients: $numClients, avg op/client: $meanNumOp, emulate WAN: $wan")

  // tweak the parallelism to execute futures (http://stackoverflow.com/a/15285441)
  implicit val ec = new ExecutionContext {
    val threadPool = Executors.newFixedThreadPool(numClients * 2);
    override def reportFailure(cause: Throwable): Unit = {};
    override def execute(runnable: Runnable): Unit =
      threadPool.submit(runnable);
    def shutdown() = threadPool.shutdown();
  }

  var containerIds = null: Array[String]
  try {
    // start cluster
    database match {
      case "zk" =>
        containerIds = ZkCluster.start(numServers)
        if (wan) ZkCluster.slowDownNetwork(containerIds)
      case "antidote" =>
        containerIds = AntidoteDBCluster.start(numServers)
        if (wan) AntidoteDBCluster.slowDownNetwork(containerIds)
      case _ => ;
    }

    // setup clients
    val testers = for (id <- 'a' to ('a' + numClients - 1).toChar) yield {
      var client: Client = database match {
        case "zk" =>
          new ZkClient().init(ZkCluster.getConnectionString(containerIds))
        case "antidote" =>
          new AntidoteDBClient()
            .init(AntidoteDBCluster.getConnectionString(numServers))
        case "reg" => DummyRegClient
        case "lin" => DummyLinClient
      }
      new Tester(id,
        meanNumOp,
        sigmaNumOp,
        maxInterOpInterval,
        readRatio,
        client)
    }

    // run execution
    val futures = new ListBuffer[Future[ListBuffer[Operation]]]
    val opLst = new ListBuffer[Operation]
    val sTime = System.nanoTime
    for (t <- testers) futures += Future(t.run(sTime))
    for (f <- futures) opLst ++= Await.result(f, Duration.Inf)
    val duration = System.nanoTime - sTime
    //println("\nResults:"); opLst.foreach(x => println(x.toLongString))

    // check and draw execution
    try {
      val (_, res) = Checker.checkExecution(opLst)
      printResults(res)
    } finally {
      Drawer.drawExecution(numClients, opLst, duration)
    }
  } catch {
    case e: Exception => e.printStackTrace
  } finally {

    ec.shutdown()

    // tear down cluster
    if (containerIds != null)
      database match {
        case "zk" =>
          ZkCluster.stop(containerIds)
        case "antidote" =>
          AntidoteDBCluster.stop(containerIds)
        case _ => ;
      }
  }

  def printResults(res: Map[Symbol, Boolean]) = {
    println(
      "Linearizability............................" + printBool(res(Checker.LIN)) + "\n" +
        "Regular...................................." + printBool(res(Checker.REG)) + "\n" +
        "Sequential................................." + printBool(res(Checker.SEQ)) + "\n" +
        "Causal....................................." + printBool(res(Checker.CAU)) + "\n" +
        "Session causality (WFR)...................." + printBool(res(Checker.WFR)) + "\n" +
        "Inter-Session Monotonicity (MR, MW)........" + printBool(res(Checker.MRW)) + "\n" +
        "Intra-Session Monotonicity (RYW)..........." + printBool(res(Checker.RYW)))
  }

  def printBool(b: Boolean): String = b match {
    case true => Console.GREEN + "[OK]" + Console.RESET
    case false => Console.RED + "[KO]" + Console.RESET
  }
}
