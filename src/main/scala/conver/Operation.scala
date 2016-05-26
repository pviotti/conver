package conver

import scala.collection.mutable.HashSet

class Operation(val proc: Char,
                val opType: OpType,
                val sTime: Long,
                val eTime: Long,
                val arg: Int,
                val notes: HashSet[String]) { 
  
  override def toString = proc + ":" + opType + ":" + arg
    
  def toLongString = 
    "[" + proc + "," + opType + "," + sTime + "," + eTime + 
    "," + arg + "," + (if (notes.isEmpty) "-" else notes.mkString(" ")) + "]" 
}

sealed trait OpType
case object READ extends OpType    { override def toString = "r" }
case object WRITE extends OpType   { override def toString = "w" }