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

class Tester(val id: Char,
    val meanNumOp: Int,
    val sigmaNumOp: Int,
    val maxInterOpInterval: Int,
    val readFraction: Int,
    val client: Client) {

  private val seed: Long = System.currentTimeMillis
  private val rnd: Random = new Random(seed)
  private val key = "key"

  private var numOp: Int = 0
  private var opLst: ListBuffer[Operation] = new ListBuffer[Operation]

  def init: Tester = { 
    client.init
    this
  }

  def run(t0: Long): ListBuffer[Operation] = {
    numOp = Math.floor(rnd.nextGaussian() * sigmaNumOp + meanNumOp).asInstanceOf[Int]
        
    for (i <- 1 to numOp) {
      // TODO better with exponential interarrival times?
      Thread.sleep(rnd.nextInt(maxInterOpInterval))     

      if (rnd.nextInt(readFraction) == 0) {
        val sTime = System.nanoTime() -t0
        val arg = client.read(key)
        val eTime = System.nanoTime() -t0
        val op = new Operation(id, READ, sTime, eTime, arg, new HashSet[String])
        opLst += op
        print(s"$op ")
      } else {
        val arg = MonotonicOracle.getNextMonotonicInt
        val sTime = System.nanoTime() -t0
        client.write(key, arg)
        val eTime = System.nanoTime() -t0
        val op = new Operation(id, WRITE, sTime, eTime, arg, new HashSet[String])
        opLst += op
        print(s"$op ")
      }
    }
    
    opLst
  }
}

object MonotonicOracle {
  private var atomicInt: AtomicInteger = new AtomicInteger(0)

  def getNextMonotonicInt: Int =
    atomicInt.getAndIncrement // returns the previous value
}