package conver

import scala.collection.mutable.ListBuffer
import scalax.collection.mutable.Graph
import scalax.collection.edge.LkDiEdge

object Checker {

  def checkExecution(opLst: ListBuffer[Operation]): ListBuffer[Operation] = {

    var g: Graph[Operation, LkDiEdge] = Graph()

    val rbCmp = (op1: Operation, op2: Operation) => op1.eTime < op2.sTime
    val soCmp = (op1: Operation, op2: Operation) => op1.proc == op2.proc && op1.eTime < op2.sTime
    val visCmp = (op1: Operation, op2: Operation) => op1.opType == WRITE && op2.opType == READ && op1.arg == op2.arg

    addEdges(opLst, g, rbCmp, "rb")
    addEdges(opLst, g, soCmp, "so")
    addEdges(opLst, g, visCmp, "vis")

    //    println(g.nodes.length)
    //    println(g.edges.length)
    println(g.edges mkString "; ")

    opLst
  }

  def addEdges(
    opLst: ListBuffer[Operation],
    g: Graph[Operation, LkDiEdge],
    cmpFun: (Operation, Operation) => Boolean, label: String): Unit =
    for (op1 <- opLst; op2 <- opLst if cmpFun(op1, op2))
      g += LkDiEdge(op1, op2)(label)

}