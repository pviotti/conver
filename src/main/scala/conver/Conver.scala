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

object Conver extends App {

  // argument parsing
  type OptionMap = Map[Symbol, Any]

  def getArgumentsMap(map: OptionMap, list: List[String]): OptionMap = {
    list match {
      case Nil => map
      case "-c" :: value :: tail =>
        getArgumentsMap(map ++ Map('client -> value), tail)
      case "-n" :: value :: tail =>
        getArgumentsMap(map ++ Map('num -> value.toInt), tail)
      case "-o" :: value :: tail =>
        getArgumentsMap(map ++ Map('op -> value.toInt), tail)
      case option :: tail =>
        println("Unknown option " + option); sys.exit(1)
    }
  }
  val options = getArgumentsMap(Map(), args.toList)

  // start cluster
  val clientType = options.getOrElse('client, "lin")
  var containerIds = null: Array[String]
  clientType match {
    case "zk" =>
      containerIds = ZkCluster.start(3)
    case _ => ;
  }

  try {
    // setup cluster, clients and testers
    val numClients = options.getOrElse('num, 3).asInstanceOf[Int]
    val meanNumOp: Int = options.getOrElse('op, 5).asInstanceOf[Int]
    val sigmaNumOp: Int = 1
    val maxInterOpInterval: Int = 100
    val readFraction: Int = 2
    implicit val ec = ExecutionContext.global
    val futures = new ListBuffer[Future[ListBuffer[Operation]]]
    val opLst = new ListBuffer[Operation]
    val testers = for (id <- 'a' to ('a' + numClients - 1).toChar) yield {
      var client: Client = clientType match {
        case "zk" =>
          new ZkClient().init(ZkCluster.getConnectionString(containerIds))
        case "reg" =>
          DummyRegClient
        case "lin" =>
          DummyLinClient
      }
      new Tester(id, meanNumOp, sigmaNumOp, maxInterOpInterval, readFraction, client).init
    }
    println(s"Run: $clientType, $numClients, $meanNumOp")

    // run execution
    val sTime = System.nanoTime
    for (t <- testers) futures += Future(t.run(sTime))
    for (f <- futures) opLst ++= Await.result(f, Duration.Inf)
    val duration = System.nanoTime - sTime
    println("\nResults:")
    opLst.foreach(x => println(x.toLongString))

    // check and draw execution
    Checker.checkExecution(opLst)
    Drawer.drawExecution(numClients, opLst, duration)
  } finally {

    // tear down cluster
    clientType match {
      case "zk" =>
        ZkCluster.stop(containerIds)
      case _ => ;
    }
    
  }
}