package conver

import org.scalatest.FunSuite
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashSet

class CheckerTest extends FunSuite {

  test("New-old inversion") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 5, 1, new HashSet[String])
    ops += new Operation('a', WRITE, 7, 15, 2, new HashSet[String])
    ops += new Operation('b', READ, 4, 8, 2, new HashSet[String])
    ops += new Operation('c', READ, 9, 12, 1, new HashSet[String])
    Checker.checkExecution(ops)
  }

  test("Crossed concurrent read-writes") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 2, 1, new HashSet[String])
    ops += new Operation('b', WRITE, 1, 2, 2, new HashSet[String])
    ops += new Operation('a', READ, 4, 6, 1, new HashSet[String])
    ops += new Operation('b', READ, 4, 6, 2, new HashSet[String])
    Drawer.drawExecution(2, ops, 8)
    Checker.checkExecution(ops)
  }

  test("Causal violation")(pending)
  test("RYW violation")(pending)
  test("MW violation")(pending)
  test("MR violation")(pending)
  test("WFR violation")(pending)

}