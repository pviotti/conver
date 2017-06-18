package conver.clients

import java.util.concurrent.ConcurrentHashMap
import scala.util.Random

/**
  * Dummy client that emulates a linearizable data store.
  */
object DummyLinClient extends Client {

  private val hashMap = new ConcurrentHashMap[String, Int]
  private val seed = System.nanoTime
  private val rnd = new Random(seed)
  private val maxLatency = 1500

  override def init(connStr: String) =
    hashMap.put(Client.KEY, Client.INIT_VALUE)

  override def read(key: String) = {
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    val ret = hashMap.get(key)
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    ret
  }

  override def write(key: String, value: Int) = {
    Thread.sleep(rnd.nextInt(maxLatency / 2))
    hashMap.put(key, value)
    Thread.sleep(rnd.nextInt(maxLatency / 2))
  }

  override def delete(key: String) =
    hashMap.remove(Client.KEY)

  override def terminate = Unit
}
