package conver

import scala.collection.mutable.ListBuffer
import scalax.collection.mutable.Graph
import scalax.collection.edge.Implicits._
import scala.collection.mutable.HashSet
import conver.clients.Client
import scalax.collection.GraphEdge.DiEdge

object Checker {

  def checkExecution(opLst: ListBuffer[Operation]): ListBuffer[Operation] = {

    val gAr: Graph[Operation, DiEdge] = Graph()

    val (isLinearizable, readAnomLst) = checkLinearizability(gAr, opLst)
    if (isLinearizable) {
      println(s"Linearizability..............................${Console.GREEN}[OK]${Console.RESET}")
      println("Total order: " + gAr.nodes.toSeq.sortBy(x => -x.outDegree))
      //println(gAr.edges mkString " ")
    } else {
      println(s"Linearizability..............................${Console.RED}[KO]${Console.RESET}")
      println("Anomalies: " + readAnomLst)
      println("Tentative total order: " + gAr.nodes.toSeq.sortBy(x => -x.outDegree))

      //      val gSo: Graph[Operation, DiEdge] = Graph()
    }
    opLst
  }

  def checkLinearizability(
    gAr: Graph[Operation, DiEdge],
    opLst: ListBuffer[Operation]) = {

    val readAnomLst = new ListBuffer[Operation]
    val sortedOps = opLst.sortBy { x => x.sTime }

    for (op <- sortedOps) {

      // add node and link to its rb-preceding
      addNodeToGraph(gAr, op)

      if (op is READ) {

        // add to graph all writes concurrent to this read
        for (op1 <- sortedOps)
          if ((op1 is WRITE) && areLinConcurrent(op, op1))
            addNodeToGraph(gAr, op1)

        // find matched write
        if (op.arg != Client.INIT_VALUE) { // read of initial value has no matching write
          val matchedW = gAr.nodes.find { x => visCmp(x.value, op) }.get

          // matched write inherits read's rb edges
          for (e <- gAr.get(op).incoming)
            if (e.source.value != matchedW.value) {
              //println(s"Inheriting ${e.source.value} -> ${matchedW.value}")
              gAr += DiEdge(e.source.value, matchedW.value)
            }

          /* Refine response time of write matching the read.
           * This allows to add rb edges and then spot possible
           * cycles due to anomalies that otherwise would go unnoticed. */
          if (op.eTimeX < matchedW.eTimeX) {
            //println(s"Refining ${matchedW.value} to $op")
            gAr.get(matchedW).value.eTimeX = op.eTimeX
          }
        }

        // remove read from graph
        gAr -= op

        if (foundAnomaly(gAr))
          readAnomLst += op
      }
    }

    // add arbitrary edges between writes that
    // have not yet been related by interleaving reads
    for (n1 <- gAr.nodes; n2 <- gAr.nodes) {
      if (n1.findOutgoingTo(n2) == None &&
        n2.findOutgoingTo(n1) == None)
        if (n1.value.sTime < n2.value.sTime) {
          //println(s"Adding ${n1.value} ~> ${n2.value}")
          gAr += DiEdge(n1.value, n2.value)
        }
    }

    // insert reads back into ar, right after corresponding writes
    opLst.filter(op => op is READ).foreach { read =>

      if (read.arg == Client.INIT_VALUE) {
        for (n <- gAr.nodes.toSeq)
          gAr += DiEdge(read, n.value)
      } else {
        var matched = false
        for (n <- gAr.nodes.toSeq.sortBy(x => -x.outDegree))
          if (!matched) {
            gAr += DiEdge(n.value, read)
            if (visCmp(n.value, read))
              matched = true
          } else {
            gAr += DiEdge(read, n.value)
          }
      }
    }

    assert(gAr.nodes.length == opLst.size)
    assert(gAr.edges.length == (opLst.size * (opLst.size - 1) / 2))

    if (!readAnomLst.isEmpty)
      (false, readAnomLst)
    else
      (true, readAnomLst)
  }

  def addNodeToGraph(g: Graph[Operation, DiEdge], op: Operation): Unit = {
    if (!g.nodes.contains(op)) {
      g.add(op)
      for (n <- g.nodes.toSeq if (linRbCmp(n.value, op)))
        g += DiEdge(n.value, op)
    }
  }

  def foundAnomaly(g: Graph[Operation, DiEdge]): Boolean = {

    if (!g.isAcyclic) {

      /* Try to break the cycle by removing
       * an edge between two operations related by rb
       * contradicting the relation.
       * If this fails (because operations are concurrent),
       * remove a random edge. */

      for (c <- g.findCycle) {
        println(s"Cycle found: $c")

        val rbEdges = c.edges.filter(e => linRbCmp(e.target.value, e.source.value))
        val remEdge = if (rbEdges.isEmpty) {
          print(s"No vertex ordered by rb found in cycle, removing random edge. ")
          c.edges.toVector(0)
        } else
          rbEdges.toVector(0)

        println(s"Removing edge from cycle: $remEdge")
        g -= remEdge
      }

      true
    } else
      false
  }

  /**
   * Compare function for return-before ordering
   * based on adjustable operation end time
   * used for graph-based linearizability checking.
   */
  def linRbCmp(op1: Operation, op2: Operation) =
    op1.eTimeX < op2.sTime

  def areLinConcurrent(op1: Operation, op2: Operation) =
    !linRbCmp(op1, op2) && !linRbCmp(op2, op1)

  def rbCmp(op1: Operation, op2: Operation) =
    op1.eTime < op2.sTime

  def soCmp(op1: Operation, op2: Operation) =
    op1.proc == op2.proc && op1.eTime < op2.sTime

  def visCmp(op1: Operation, op2: Operation) =
    (op1 is WRITE) && (op2 is READ) && op1.arg == op2.arg

  def addEdges(
    opLst: ListBuffer[Operation],
    g: Graph[Operation, DiEdge],
    cmpFun: (Operation, Operation) => Boolean) =
    for (op1 <- opLst; op2 <- opLst if cmpFun(op1, op2)) // n^2
      g += DiEdge(op1, op2)

}