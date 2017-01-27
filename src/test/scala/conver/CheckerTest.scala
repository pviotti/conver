package conver

import org.scalatest.FunSuite
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashSet

class CheckerTest extends FunSuite {

  val tstDir = "test-exec/"

  test("Linearizable") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 5, 1)
    ops += new Operation('a', WRITE, 7, 15, 2)
    ops += new Operation('b', READ, 4, 8, 2)
    ops += new Operation('c', READ, 9, 12, 2)
    val (_, cons) = Checker.checkExecution(ops)
    assert(cons(Checker.LIN)); assert(cons(Checker.REG))
    assert(cons(Checker.SEQ)); assert(cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(3, ops, 16, tstDir + "/01-lin.png")
  }

  test("Regular (new-old inversion)") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 5, 1)
    ops += new Operation('a', WRITE, 7, 15, 2)
    ops += new Operation('b', READ, 4, 8, 2)
    ops += new Operation('c', READ, 9, 12, 1)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(cons(Checker.REG))
    assert(cons(Checker.SEQ)); assert(cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(3, ops, 16, tstDir + "/02-reg-new-old_inv.png")
  }

  test("Sequential (stale read)") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', READ, 6, 7, 1)
    ops += new Operation('b', WRITE, 4, 5, 2)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(cons(Checker.SEQ)); assert(cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(3, ops, 8, tstDir + "/03-seq.png")
  }

  test("Sequential (stale read: crossed concurrent read-writes)") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 2, 1)
    ops += new Operation('b', WRITE, 1, 2, 2)
    ops += new Operation('a', READ, 4, 6, 1)
    ops += new Operation('b', READ, 4, 6, 2)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(cons(Checker.SEQ)); assert(cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(2, ops, 8, tstDir + "/03-seq-crossed_conc.png")
  }

  test("Causal (global total order violation)") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 2, 1)
    ops += new Operation('b', WRITE, 3, 4, 2)
    ops += new Operation('a', READ, 5, 6, 1)
    ops += new Operation('a', READ, 7, 8, 2)
    ops += new Operation('b', READ, 9, 10, 2)
    ops += new Operation('b', READ, 11, 12, 1)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(!cons(Checker.SEQ)); assert(cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(2, ops, 13, tstDir + "/04-causal-not-totorder.png")
  }

  test("Inter-session monotonicity (MR/MW) violation") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', WRITE, 5, 7, 2)
    ops += new Operation('b', READ, 9, 12, 2)
    ops += new Operation('b', READ, 13, 15, 1)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(!cons(Checker.SEQ)); assert(!cons(Checker.CAU))
    assert(!cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(2, ops, 16, tstDir + "/05-mrmw.png")
  }

  test("Inter-session monotonicity (MR/MW) violation #2") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', WRITE, 5, 7, 2)
    ops += new Operation('b', READ, 3, 6, 2)
    ops += new Operation('b', READ, 8, 11, 1)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(!cons(Checker.SEQ)); assert(!cons(Checker.CAU))
    assert(!cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(2, ops, 13, tstDir + "/05-mrmw-2.png")
  }

  test("RYW violation") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('a', WRITE, 1, 3, 1)
    ops += new Operation('a', WRITE, 5, 7, 2)
    ops += new Operation('a', READ, 9, 12, 1)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(!cons(Checker.SEQ)); assert(!cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(cons(Checker.WFR))
    assert(!cons(Checker.RYW))
    Drawer.drawExecution(1, ops, 13, tstDir + "/06-ryw.png")
  }

  test("WFR violation") {
    val ops = new ListBuffer[Operation]
    ops += new Operation('b', WRITE, 1, 3, 1)
    ops += new Operation('c', READ, 4, 7, 1)
    ops += new Operation('c', WRITE, 8, 11, 2)
    ops += new Operation('a', READ, 8, 11, 2)
    ops += new Operation('a', READ, 12, 14, 1)
    val (_, cons) = Checker.checkExecution(ops)
    assert(!cons(Checker.LIN)); assert(!cons(Checker.REG))
    assert(!cons(Checker.SEQ)); assert(!cons(Checker.CAU))
    assert(cons(Checker.MRW)); assert(!cons(Checker.WFR))
    assert(cons(Checker.RYW))
    Drawer.drawExecution(3, ops, 15, tstDir + "/07-wfr.png")
  }

}