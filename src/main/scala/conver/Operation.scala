package conver

import scala.collection.mutable.HashSet

class Operation(
    val proc: Char,
    val opType: OpType,
    val sTime: Long,
    val eTime: Long,
    val arg: Int) {

  val latency = eTime - sTime
  /* eTimeX: mutable operation end time,
   * used to perform graph-based consistency checks */
  var eTimeX = eTime
  val anomalies: HashSet[String] = new HashSet[String]

  def is(opt: OpType) = this.opType == opt

  override def toString = proc + ":" + opType + ":" + arg

  def toLongString: String =
    "[" + proc + "," + opType + "," + arg + "," + sTime + "," + eTime + "," +
      (if (anomalies.isEmpty) "-" else anomalies.mkString(" ")) + "]"

  def toLabelString: String =
    opType match {
      case READ => opType.toString.toUpperCase + ":" + arg
      case _ => opType.toString.toUpperCase + "(" + arg + ")"
    }

}

sealed trait OpType
case object READ extends OpType { override def toString = "r" }
case object WRITE extends OpType { override def toString = "w" }