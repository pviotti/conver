package conver

import scala.collection.mutable.HashSet

class Operation(
    val proc: Char,
    val opType: OpType,
    val sTime: Long,
    val eTime: Long,
    val arg: Int,
    val notes: HashSet[String]) {

  val latency = eTime - sTime

  /* mutable operation end time,
   * used to perform graph-based consistency checks */
  var eTimeX = eTime

  def is(opt: OpType) = this.opType == opt

  override def toString = proc + ":" + opType + ":" + arg

  def toLongString: String =
    "[" + proc + "," + opType + "," + arg + "," + sTime + "," + eTime +
      (if (notes.isEmpty) "-" else notes.mkString(" ")) + "]"

  def toLabelString: String =
    opType match {
      case READ => opType.toString.toUpperCase + ":" + arg
      case _ => opType.toString.toUpperCase + "(" + arg + ")"
    }

}

sealed trait OpType
case object READ extends OpType { override def toString = "r" }
case object WRITE extends OpType { override def toString = "w" }