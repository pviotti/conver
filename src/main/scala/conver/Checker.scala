package conver

import scala.collection.mutable.ListBuffer
import scalax.collection.mutable.Graph
import scalax.collection.edge.Implicits._
import scala.collection.mutable.HashSet
import conver.clients.Client
import scalax.collection.GraphEdge.DiEdge

object Checker {

  val ANOMALY_REGULAR = "reg"
  val ANOMALY_STALEREAD = "stale"

  def checkExecution(opLst: ListBuffer[Operation]): ListBuffer[Operation] = {

    val gAr: Graph[Operation, DiEdge] = Graph()

    val (isLinearizable, readAnomLst) = checkLinearizability(gAr, opLst)
    println(s"Linearizability.............................." + printBoolean(isLinearizable))
    if (isLinearizable) {
      println("Total order: " + gAr.nodes.toSeq.sortBy(x => -x.outDegree))
      //println(gAr.edges mkString " ")
    } else {
      val isRegular = readAnomLst.find(op => op.notes.contains(ANOMALY_STALEREAD)).isEmpty
      println(s"Regular......................................" + printBoolean(isRegular))

      val gRb: Graph[Operation, DiEdge] = Graph()
      addEdges(opLst, gRb, rbCmp)

      val gSo: Graph[Operation, DiEdge] = Graph()
      addEdges(opLst, gSo, soCmp)

      val gVis: Graph[Operation, DiEdge] = Graph()
      addEdges(opLst, gVis, visCmp)

      val gSoVis = gSo.union(gVis)
      val isCausal = gAr.intersect(gSoVis) == gSoVis
      println(s"Causal......................................." + printBoolean(isCausal))

      val isPRAM = gAr.intersect(gSo) == gSo
      println(s"PRAM........................................." + printBoolean(isPRAM))

      if (isRegular) { assert(isCausal); assert(isPRAM) }

      println("Anomalies: " + readAnomLst)
      println("Tentative total order: " + gAr.nodes.toSeq.sortBy(x => -x.outDegree))
    }
    opLst
  }

  def printBoolean(b: Boolean): String = b match {
    case true => Console.GREEN + "[OK]" + Console.RESET
    case false => Console.RED + "[KO]" + Console.RESET
  }

  /**
   * Linearizability checker
   * Implements an algorithm similar to that of
   * Lu et al., SOSP '15
   */
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
          val matchedW = gAr.nodes.find(x => visCmp(x.value, op)).get

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
            println(s"Refining ${matchedW.value} to $op")
            gAr.get(matchedW).value.eTimeX = op.eTimeX
          }
        }

        // remove read from graph
        gAr -= op

        val (isLinear, anomalyType) = checkGraph(gAr)
        if (!isLinear) {
          op.notes += anomalyType
          readAnomLst += op
        }
      }
    }

    // add arbitrary edges between writes that
    // have not been ordered by interleaving reads
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
      g += op
      for (n <- g.nodes.toSeq if (linRbCmp(n.value, op)))
        g += DiEdge(n.value, op)
    }
  }

  def checkGraph(g: Graph[Operation, DiEdge]): (Boolean, String) = {

    if (!g.isAcyclic) {

      /* Try to break the cycle by removing
       * an edge between two operations related by rb
       * contradicting the relation.
       * If this fails (because operations are concurrent),
       * remove a random edge. */

      val c = g.findCycle.getOrElse(throw new IllegalStateException("No cycles found"))
      println(s"Cycle found: $c")

      /* If the cycle contains writes whose end times
       * have been refined, then the anomaly rules out
       * linearizability but not regular semantics. */
      val isStaleRead = c.nodes.find(n =>
        (n.value.eTime > n.value.eTimeX) ||
          (n.value.eTime > n.value.eTimeX)).isEmpty
      val anomalyType = if (isStaleRead) ANOMALY_STALEREAD else ANOMALY_REGULAR

      val rbEdges = c.edges.filter(e => linRbCmp(e.target.value, e.source.value))
      val remEdge = if (rbEdges.isEmpty) {
        print(s"No vertex ordered by rb found in cycle, removing random edge. ")
        c.edges.head
      } else
        rbEdges.head

      println(s"Removing edge from cycle: $remEdge")
      g -= remEdge

      (false, anomalyType)
    } else
      (true, "")
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