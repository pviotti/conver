package conver

import conver.clients.DummyClient
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import conver.clients.Client
import scala.collection.mutable.HashSet

object Conver extends App {

  implicit val ec = ExecutionContext.global

  // params of execution - TODO arguments parsing
  val client: Client = DummyClient
  val numClients: Int = 3
  val meanNumOp: Int = 10
  val sigmaNumOp: Int = 2
  val maxInterOpInterval: Int = 2000
  val readFraction: Int = 2

  val futures = new ListBuffer[Future[ListBuffer[Operation]]]
  val opLst = new ListBuffer[Operation]

  val testers = for (id <- 'a' to ('a' + numClients - 1).toChar)
    yield new Tester(id, meanNumOp, sigmaNumOp, maxInterOpInterval, readFraction, client).init

  val sTime = System.nanoTime
  for (t <- testers)
    futures += Future { t.run(sTime) }

  for (f <- futures)
    opLst ++= Await.result(f, Duration.Inf)
  val duration = System.nanoTime - sTime

  println("\nResults:")
  opLst.foreach { x => println(x.toLongString) }
  
  Checker.checkExecution(opLst)
  Drawer.drawExecution(numClients, opLst, duration)
}