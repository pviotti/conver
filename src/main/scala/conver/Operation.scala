package conver

import scala.collection.mutable.HashSet

class Operation(
    val proc: Char,
    val opType: OpType,
    val sTime: Long,
    val eTime: Long,
    val arg: Int,
    val notes: HashSet[String]) {

  override def toString = proc + ":" + opType + ":" + arg

  def toLongString: String =
    "[" + proc + "," + opType + "," + sTime + "," + eTime +
      "," + arg + "," + (if (notes.isEmpty) "-" else notes.mkString(" ")) + "]"

  def toLabelString: String =
    opType match {
      case READ => opType.toString.toUpperCase + ":" + arg
      case _ => opType.toString.toUpperCase + "(" + arg + ")"
    }

}

sealed trait OpType
case object READ extends OpType { override def toString = "r" }
case object WRITE extends OpType { override def toString = "w" }