package conver

import scala.collection.mutable.ListBuffer
import scalax.collection.mutable.Graph
import scalax.collection.edge.Implicits._
import scala.collection.mutable.HashSet
import conver.clients.Client
import scalax.collection.edge.LkDiEdge

object Checker {

  val ANOMALY_REGULAR = "reg"
  val ANOMALY_STALEREAD = "stale"

  val AR = "AR"; val RB = "RB"; val CONC = "CONC"; val VIS = "VIS"; val SO = "SO"

  def checkExecution(opLst: ListBuffer[Operation]): ListBuffer[Operation] = {

    val g: Graph[Operation, LkDiEdge] = Graph()

    val (isLinearizable, readAnomLst) = checkLinearizability(g, opLst)
    print("Total order: ")
    g.nodes.toSeq.sortBy(x => -x.outDegree).foreach(x => print(x + " ")); println
    println("Linearizability.............................." + printBoolean(isLinearizable))

    val isRegular = readAnomLst.find(op => op.anomalies.contains(ANOMALY_STALEREAD)).isEmpty
    println("Regular......................................" + printBoolean(isRegular))

    //TODO include initial ghost write

    addEdges(opLst, g, rbCmp, RB)
    addEdges(opLst, g, soCmp, SO)
    addEdges(opLst, g, visCmp, VIS)
    //addEdges(opLst, g, areConcurrent, CONC)

    // ending "map" to cast labeled edges to normal ones before doing set operations
    val eRb = g.edges.filter(e => e.label.equals(RB)).map(e => e.source -> e.target)
    val eVis = g.edges.filter(e => e.label.equals(VIS)).map(e => e.source -> e.target)
    val eSo = g.edges.filter(e => e.label.equals(SO)).map(e => e.source -> e.target)
    //val eAr = g.edges.filter(e => e.label.equals(AR)).map(e => e.source -> e.target)
    //val eSoRW = g.edges.filter(e => e.label.equals(SO) &&
    //  (e.source.value is READ) && (e.target.value is WRITE)).map(e => e.source -> e.target)
    //val eSoWW = g.edges.filter(e => e.label.equals(SO) &&
    //  (e.source.value is WRITE) && (e.target.value is WRITE)).map(e => e.source -> e.target)
    //val eSoRR = g.edges.filter(e => e.label.equals(SO) &&
    //  (e.source.value is READ) && (e.target.value is READ)).map(e => e.source -> e.target)
    //val eVisSoRR = eSoRR.union(eVis)
    //val eVisSoRW = eSoRW.union(eVis)
    //val eVisSoWW = eSoWW.union(eVis)
    //val eVisSo = eSo.union(eVis)

    /* Inter-session (returns-before) monotonicity:
       * maintain returns-before write order across sessions
       * (i.e. reads and writes belong to different sessions).
       * Includes: Monotonic Writes, Monotonic Reads, Writes Follow Reads */
    var isInterSessMonotonic = true
    for ((w1, r1) <- eVis; (w2, r2) <- eVis)
      if (eRb.contains((w1, w2)) && (eSo.contains(r2, r1))
        && (w1.value.proc != r2.value.proc))
        isInterSessMonotonic = false

    /* Intra-session (returns-before) monotonicity:
       * maintain returns-before write order
       * for reads and writes belonging to the same session.
       * Includes: Read-Your-Writes */
    var isIntraSessMonotonic = true
    for ((w0, r0) <- eVis; (w1, r0) <- eSo)
      if ((w1.value is WRITE) && eRb.contains((w0, w1)))
        isIntraSessMonotonic = false

    //val isMonotonicWrites = eAr.intersect(eSoWW) == eSoWW
    //val isMonotonicReads = eAr.intersect(eVisSoRR) == eVisSoRR
    //val isWritesFollowReads = eAr.intersect(eVisSoRW) == eVisSoRW
    //val isCausal = eAr.intersect(eVisSo) == eVisSo && isPRAM
    //val isPRAM = eAr.intersect(eSo) == eSo

    val isCausal = isInterSessMonotonic && isIntraSessMonotonic

    println("Causal......................................." + printBoolean(isCausal))
    println("Inter-Session Monotonic (MR, MW, WFR)........" + printBoolean(isInterSessMonotonic))
    println("Intra-Session Monotonic (RYW)................" + printBoolean(isIntraSessMonotonic))

    if (isLinearizable) assert(isRegular)
    if (isRegular) {
      assert(isCausal); assert(isInterSessMonotonic); assert(isIntraSessMonotonic)
    }

    print("Anomalies: "); readAnomLst.foreach(x => print(x + " ")); println
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
    g: Graph[Operation, LkDiEdge],
    opLst: ListBuffer[Operation]) = {

    val readAnomLst = new ListBuffer[Operation]
    val sortedOps = opLst.sortBy { x => x.sTime }

    for (op <- sortedOps) {

      // add node and link to its rb-preceding
      addNodeToGraph(g, op)

      if (op is READ) {

        // add to graph all writes concurrent to this read
        for (op1 <- sortedOps)
          if ((op1 is WRITE) && areLinConcurrent(op, op1))
            addNodeToGraph(g, op1)

        if (op.arg != Client.INIT_VALUE) { // read of initial value has no matching write
          // find matched write
          val matchedW = g.nodes.find(x => visCmp(x.value, op)).get

          // matched write inherits read's rb edges
          for (e <- g.get(op).incoming)
            if (e.source.value != matchedW.value) {
              //println(s"Inheriting ${e.source.value} -> ${matchedW.value}")
              g += LkDiEdge(e.source.value, matchedW.value)(AR)
            }

          /* Refine response time of write matching the read.
           * This allows to add further returns-before edges
           * and then spot possible cycles due to anomalies
           * that otherwise would go unnoticed (e.g. new-old inversion). */
          if (op.eTimeX < matchedW.value.eTimeX) {
            println(s"Refining ${matchedW.value} to $op")
            matchedW.value.eTimeX = op.eTimeX
          }
        }

        // remove read from graph
        g -= op

        val (isLinear, anomalyType) = checkGraph(g, op)
        if (!isLinear) {
          op.anomalies += anomalyType
          readAnomLst += op
        }
      }
    }

    // add arbitrary edges between writes that
    // have not been ordered by interleaving reads
    for (n1 <- g.nodes; n2 <- g.nodes) {
      if (n1.findOutgoingTo(n2) == None &&
        n2.findOutgoingTo(n1) == None)
        if (n1.value.sTime < n2.value.sTime) {
          //println(s"Adding ${n1.value} ~> ${n2.value}")
          g += LkDiEdge(n1.value, n2.value)(AR)
        }
    }

    /* insert reads back into ar:
     *  - right after corresponding writes in case they don't present anomalies, or
     *  - according to returns-before order in case they do */
    opLst.filter(op => op is READ).foreach { read =>

      if (read.anomalies.isEmpty) {

        if (read.arg == Client.INIT_VALUE) {
          for (n <- g.nodes.toSeq)
            g += LkDiEdge(read, n.value)(AR)
        } else {
          var matched = false
          for (n <- g.nodes.toSeq.sortBy(x => -x.outDegree))
            if (!matched) {
              g += LkDiEdge(n.value, read)(AR)
              if (visCmp(n.value, read))
                matched = true
            } else {
              g += LkDiEdge(read, n.value)(AR)
            }
        }

      } else {

        for (n <- g.nodes.toSeq.sortBy(x => -x.outDegree)) {
          if (rbCmp(n.value, read))
            g += LkDiEdge(n.value, read)(AR)
          else if (rbCmp(read, n.value))
            g += LkDiEdge(read, n.value)(AR)
          else if (areConcurrent(read, n.value))
            // process id breaks ties for concurrent operations
            if (read.proc > n.value.proc) g += LkDiEdge(read, n.value)(AR)
            else g += LkDiEdge(n.value, read)(AR)
        }

      }
    }

    assert(g.nodes.length == opLst.size)
    assert(g.edges.length == (opLst.size * (opLst.size - 1) / 2))

    if (!readAnomLst.isEmpty)
      (false, readAnomLst)
    else
      (true, readAnomLst)
  }

  def addNodeToGraph(g: Graph[Operation, LkDiEdge], op: Operation): Unit = {
    if (!g.nodes.contains(op)) {
      g += op
      for (n <- g.nodes.toSeq if (linRbCmp(n.value, op)))
        g += LkDiEdge(n.value, op)(AR)
    }
  }

  def checkGraph(g: Graph[Operation, LkDiEdge], currentRead: Operation): (Boolean, String) = {

    if (!g.isAcyclic) {

      /* Try to break the cycle by removing
       * an edge between two operations related by rb
       * contradicting the relation.
       * If this fails (because operations are concurrent),
       * remove a random edge. */

      val c = g.findCycle.getOrElse(throw new IllegalStateException("No cycles found"))
      println(s"Cycle found: $c")

      /* If the cycle contains writes that are concurrent
       * with the read just checked, then the anomaly rules out
       * linearizability but not regular semantics. */
      val isStaleRead = !c.nodes.exists(x => areConcurrent(x.value, currentRead))
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

  def areConcurrent(op1: Operation, op2: Operation) =
    !rbCmp(op1, op2) && !rbCmp(op2, op1)

  def soCmp(op1: Operation, op2: Operation) =
    op1.proc == op2.proc && op1.eTime < op2.sTime

  def visCmp(op1: Operation, op2: Operation) =
    (op1 is WRITE) && (op2 is READ) && op1.arg == op2.arg

  def addEdges(
    opLst: ListBuffer[Operation],
    g: Graph[Operation, LkDiEdge],
    cmpFun: (Operation, Operation) => Boolean,
    label: String) =
    for (op1 <- opLst; op2 <- opLst if cmpFun(op1, op2)) // n^2
      g += LkDiEdge(op1, op2)(label)

}