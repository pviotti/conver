package conver

import org.scalatest.FunSuite
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashSet

class CheckerTest extends FunSuite {

  test("New-old inversion") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 5, 1)
    ops += new Operation('a', WRITE, 7, 15, 2)
    ops += new Operation('b', READ, 4, 8, 2)
    ops += new Operation('c', READ, 9, 12, 1)
    Checker.checkExecution(ops)
    Drawer.drawExecution(3, ops, 16, "execs/new-old_inv.png")
  }

  test("Crossed concurrent read-writes") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 2, 1)
    ops += new Operation('b', WRITE, 1, 2, 2)
    ops += new Operation('a', READ, 4, 6, 1)
    ops += new Operation('b', READ, 4, 6, 2)
    Checker.checkExecution(ops)
    Drawer.drawExecution(2, ops, 8, "execs/crossed_conc.png")
  }

  test("Inter-session monotonicity (MR/MW/WFR) violation") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', WRITE, 5, 7, 2)
    ops += new Operation('b', READ, 9, 12, 2)
    ops += new Operation('b', READ, 13, 15, 1)
    Checker.checkExecution(ops)
    Drawer.drawExecution(2, ops, 16, "execs/intersess_mono.png")
  }

  test("RYW violation") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', WRITE, 5, 7, 2)
    ops += new Operation('a', READ, 9, 12, 1)
    Checker.checkExecution(ops)
    Drawer.drawExecution(1, ops, 13, "execs/ryw.png")
  }

  test("Inter-session monotonicity (MR/MW/WFR) violation #2") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', WRITE, 5, 7, 2)
    ops += new Operation('b', READ, 3, 6, 2)
    ops += new Operation('b', READ, 8, 11, 1)
    try { Checker.checkExecution(ops) }
    finally { Drawer.drawExecution(2, ops, 13, "execs/tocheck.png") }
  }

  test("WFR violation") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('b', WRITE, 1, 3, 1)
    ops += new Operation('c', READ, 4, 7, 1)
    ops += new Operation('c', WRITE, 8, 11, 2)
    ops += new Operation('a', READ, 8, 11, 2)
    ops += new Operation('a', READ, 12, 14, 1)
    Checker.checkExecution(ops)
    Drawer.drawExecution(3, ops, 15, "execs/wfr.png")
  }

  test("Linearizable")(pending)
  test("Causal violation")(pending)

}