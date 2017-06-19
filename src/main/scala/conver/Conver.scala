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
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ArrayBuffer

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
  val batch = opt[Int](short = 'b', descr = "Number of batch executions")

  verify()
}

object Conver extends App with LazyLogging {

  val conf = new Conf(args)
  val database = conf.database()
  val numServers = conf.numServers()
  val numClients = conf.numClients()
  val meanNumOp = conf.meanNumOps()
  val wan = conf.wan()
  val (isBatch, numBatch) = conf.batch.toOption match {
    case Some(num) => (true, num)
    case None => (false, 1)
  }
  val sigmaNumOp = 1
  val maxInterOpInterval = if (wan) ZkCluster.meanDelay else 20
  val readRatio = 50 // %
  logger.info(s"Started. Database: $database, servers: $numServers, " +
    s"clients: $numClients, avg op/client: $meanNumOp, emulate WAN: $wan" +
    (if (isBatch) s", batch: $numBatch" else ""))

  // tweak the parallelism to execute futures (http://stackoverflow.com/a/15285441)
  implicit val ec = new ExecutionContext {
    val threadPool = Executors.newFixedThreadPool(numClients * 2);
    override def reportFailure(cause: Throwable): Unit = {};
    override def execute(runnable: Runnable): Unit =
      threadPool.submit(runnable);
    def shutdown() = threadPool.shutdown();
  }

  val bufNumOp = ArrayBuffer[Int]()
  val bufNumFailW = ArrayBuffer[Int]()
  val bufNumFailR = ArrayBuffer[Int]()
  val bufNumAnomalies = ArrayBuffer[Int]()

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

    for (i <- 1 to numBatch) {

      if (isBatch)
        logger.info(s"-----------------------------------\nBatch run $i")

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
        new Tester(id, meanNumOp, sigmaNumOp,
          maxInterOpInterval, readRatio, client)
      }

      val futures = new ListBuffer[Future[ListBuffer[Operation]]]
      val opLst = new ListBuffer[Operation]
      MonotonicOracle.reset

      // run execution
      val sTime = System.nanoTime
      for (t <- testers) futures += Future(t.run(sTime))
      for (f <- futures) opLst ++= Await.result(f, Duration.Inf)
      val duration = System.nanoTime - sTime
      //println("\nResults:"); opLst.foreach(x => println(x.toLongString))

      // check and draw execution
      try {
        val (_, res) = Checker.checkExecution(opLst)

        bufNumOp += opLst.size
        bufNumFailW += opLst.count(x => x.anomalies.contains(Checker.ANOMALY_FAIL) && (x is WRITE))
        bufNumFailR += opLst.count(x => x.anomalies.contains(Checker.ANOMALY_FAIL) && (x is READ))
        bufNumAnomalies += opLst.count(x => x.anomalies.contains(Checker.ANOMALY_STALEREAD) || x.anomalies.contains(Checker.ANOMALY_REGULAR))
        logger.info("Operations: " + bufNumOp(i - 1))
        logger.info("Failed writes: " + bufNumFailW(i - 1) + " (" + (100 * bufNumFailW(i - 1) / bufNumOp(i - 1)) + "%)")
        logger.info("Failed reads: " + bufNumFailR(i - 1) + " (" + (100 * bufNumFailR(i - 1) / bufNumOp(i - 1)) + "%)")
        logger.info("Anomalies: " + bufNumAnomalies(i - 1) + " (" + (100 * bufNumAnomalies(i - 1) / bufNumOp(i - 1)) + "%)")

        if (isBatch) 
          logger.info("Consistency: " + getShortResultString(res))
        else 
          logger.info(getResultString(res))
      } finally {
        if (isBatch)
          Drawer.drawExecution(numClients, opLst, duration, "exec" + i + ".png")
        else
          Drawer.drawExecution(numClients, opLst, duration)
      }
    } // end batch execution

    if (isBatch) {
      logger.info(s"===================================\nEnd of batch")
      val avgOp = bufNumOp.sum.asInstanceOf[Float] / bufNumOp.size
      val avgFailW = bufNumFailW.sum.asInstanceOf[Float] / bufNumFailW.size
      val avgFailR = bufNumFailR.sum.asInstanceOf[Float] / bufNumFailR.size
      val avgAnomalies = bufNumAnomalies.sum / bufNumAnomalies.size
      logger.info("Avg operations: " + avgOp)
      logger.info("Avg failed writes: " + avgFailW + " (" + (100 * avgFailW / avgOp) + "%)")
      logger.info("Avg failed reads: " + avgFailR + " (" + (100 * avgFailR / avgOp) + "%)")
      logger.info("Avg anomalies: " + avgAnomalies + " (" + (100 * avgAnomalies / avgOp) + "%)")
    }

  } catch {
    case e: Exception => logger.error("Exception: ", e)
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

  def getShortResultString(res: Map[Symbol, Boolean]): String = {
    (if (res(Checker.LIN)) "LIN " else "") + (if (res(Checker.REG)) "REG " else "") +
      (if (res(Checker.SEQ)) "SEQ " else "") + (if (res(Checker.CAU)) "CAU " else "") +
      (if (res(Checker.WFR)) "WFR " else "") + (if (res(Checker.MRW)) "MRW " else "") +
      (if (res(Checker.RYW)) "RYW " else "")
  }

  def getResultString(res: Map[Symbol, Boolean]): String = {
    "Linearizability............................" + getBoolStr(res(Checker.LIN)) + "\n" +
      "Regular...................................." + getBoolStr(res(Checker.REG)) + "\n" +
      "Sequential................................." + getBoolStr(res(Checker.SEQ)) + "\n" +
      "Causal....................................." + getBoolStr(res(Checker.CAU)) + "\n" +
      "Session causality (WFR)...................." + getBoolStr(res(Checker.WFR)) + "\n" +
      "Inter-Session Monotonicity (MR, MW)........" + getBoolStr(res(Checker.MRW)) + "\n" +
      "Intra-Session Monotonicity (RYW)..........." + getBoolStr(res(Checker.RYW))
  }

  def getBoolStr(b: Boolean): String = b match {
    case true => Console.GREEN + "[OK]" + Console.RESET
    case false => Console.RED + "[KO]" + Console.RESET
  }
}
