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

object Conver extends App {

  val arglist = args.toList
  type OptionMap = Map[Symbol, Any]

  def getArgumentsMap(map: OptionMap, list: List[String]): OptionMap = {
    list match {
      case Nil => map
      case "-c" :: value :: tail =>
        val cli = value match {
          case "reg" => DummyRegClient
          case "lin" => DummyLinClient
        }
        getArgumentsMap(map ++ Map('client -> cli), tail)
      case "-n" :: value :: tail =>
        getArgumentsMap(map ++ Map('num -> value.toInt), tail)
      case "-o" :: value :: tail =>
        getArgumentsMap(map ++ Map('op -> value.toInt), tail)
      //case string :: Nil => getArgumentsMap(map ++ Map('infile -> string), list.tail)
      case option :: tail => println("Unknown option " + option); sys.exit(1)
    }
  }
  val options = getArgumentsMap(Map(), arglist)

  val client = options.getOrElse('client, DummyRegClient).asInstanceOf[Client]
  val numClients = options.getOrElse('num, 3).asInstanceOf[Int]
  val meanNumOp: Int = options.getOrElse('op, 5).asInstanceOf[Int]
  val sigmaNumOp: Int = 1
  val maxInterOpInterval: Int = 100
  val readFraction: Int = 2
  println(s"Run: $client, $numClients, $meanNumOp")

  implicit val ec = ExecutionContext.global
  val futures = new ListBuffer[Future[ListBuffer[Operation]]]
  val opLst = new ListBuffer[Operation]

  val testers = for (id <- 'a' to ('a' + numClients - 1).toChar)
    yield new Tester(id, meanNumOp, sigmaNumOp, maxInterOpInterval, readFraction, client).init

  val sTime = System.nanoTime
  for (t <- testers)
    futures += Future(t.run(sTime))

  for (f <- futures)
    opLst ++= Await.result(f, Duration.Inf)
  val duration = System.nanoTime - sTime

  println("\nResults:")
  opLst.foreach(x => println(x.toLongString))

  try {
    Checker.checkExecution(opLst)
  } finally {
    Drawer.drawExecution(numClients, opLst, duration)
  }
}