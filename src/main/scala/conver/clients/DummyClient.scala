package conver.clients

import java.util.concurrent.ConcurrentHashMap
import scala.util.Random

object DummyClient extends Client {

  val hashMap = new ConcurrentHashMap[String, Int]
  private val seed: Long = System.currentTimeMillis
  private val rnd: Random = new Random(seed)
  private val maxLatency: Int = 1500

  override def init() = hashMap.put("key", 0)
  override def read(key: String) = {
    Thread.sleep(rnd.nextInt(maxLatency))
    hashMap.get(key)
  }
  override def write(key: String, value: Int) = {
    hashMap.put(key, value)
    Thread.sleep(rnd.nextInt(maxLatency)) 
  }
  override def delete(key: String) = hashMap.remove(key)
  override def terminate() = Unit
}