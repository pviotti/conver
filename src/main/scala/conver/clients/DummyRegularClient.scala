package conver.clients

import java.util.concurrent.ConcurrentHashMap
import scala.util.Random
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicInteger
import java.util.HashSet
import scalax.collection.mutable.ArraySet
import java.util.LinkedList
import scala.collection.mutable.LinkedHashSet

/**
 * Dummy client that emulates a data store
 * implementing regular register semantics.
 */
object DummyRegClient extends Client {

  private val hashMap = new ConcurrentHashMap[String, Int]
  private val seed = System.nanoTime
  private val rnd = new Random(seed)
  private val maxLatency = 500
  private val concWriters = new AtomicInteger(0)

  // create concurrent HashSet (Java 8)
  private val concVals = ConcurrentHashMap.newKeySet[Int]

  override def init(connStr: String) =
    hashMap.put(Client.KEY, Client.INIT_VALUE)

  override def read(key: String) = {
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    val current = hashMap.get(key)
    val ret = if (concWriters.get == 0)
      current
    else {
      val legalWrites = new LinkedList[Int](concVals)
      if (!legalWrites.contains(current))
        legalWrites.add(current)
      print(s"(conc: $legalWrites) ")
      legalWrites.get(rnd.nextInt(legalWrites.size))
    }
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    ret
  }

  override def write(key: String, value: Int) = {
    concWriters.incrementAndGet
    concVals.add(value)
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    hashMap.put(key, value)
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    concVals.remove(value)
    concWriters.decrementAndGet
  }

  override def delete(key: String) =
    hashMap.remove(key)

  override def terminate = Unit
}