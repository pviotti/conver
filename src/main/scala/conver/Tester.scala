package conver

import java.util.Timer
import java.util.TimerTask
import java.util.Date
import scala.util.Random
import conver.clients.Client
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.LazyLogging

class Tester(val id: Char,
    val meanNumOp: Int,
    val sigmaNumOp: Int,
    val maxInterOpInterval: Int,
    val readRatio: Int,
    val client: Client) extends LazyLogging {

  private val seed = System.nanoTime
  private val rnd = new Random(seed)

  private var numOp = 0
  private var opLst: ListBuffer[Operation] = null

  def run(t0: Long): ListBuffer[Operation] = {
    numOp = Math.floor(rnd.nextGaussian * sigmaNumOp + meanNumOp).asInstanceOf[Int]
    opLst = new ListBuffer[Operation]

    for (i <- 1 to numOp) {
      // TODO better with exponential interarrival times?
      Thread.sleep(rnd.nextInt(maxInterOpInterval))

      if (rnd.nextInt(100) <= readRatio) {
        var anomaly = false
        var arg = Client.INIT_VALUE;
        val sTime = System.nanoTime - t0
        try {
          arg = client.read(Client.KEY)
        } catch {
          case e: Exception => anomaly = true
        } finally {
          val eTime = System.nanoTime - t0
          val op = new Operation(id, READ, sTime, eTime, arg)
          if (anomaly) {
            op.anomalies += Checker.ANOMALY_FAIL
            logger.debug("Read operation failed: " + op.toLongString)
          }
          opLst += op
          //print(s"$op ")
        }
      } else {
        var anomaly = false
        val arg = MonotonicOracle.getNextMonotonicInt
        val sTime = System.nanoTime - t0
        try {
          client.write(Client.KEY, arg)
        } catch {
          case e: Exception => anomaly = true
        } finally {
          val eTime = System.nanoTime - t0
          val op = new Operation(id, WRITE, sTime, eTime, arg)
          if (anomaly) {
            op.anomalies += Checker.ANOMALY_FAIL
            logger.debug("Write operation failed: " + op.toLongString)
          }
          opLst += op
          //print(s"$op ")
        }
      }

    }

    client.terminate

    opLst
  }
}

object MonotonicOracle {
  private var atomicInt = new AtomicInteger(Client.INIT_VALUE + 1)

  def reset = atomicInt.set(Client.INIT_VALUE + 1)
  def getNextMonotonicInt: Int =
    atomicInt.getAndIncrement // returns the previous value
}
