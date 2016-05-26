package conver.clients

import java.util.concurrent.ConcurrentHashMap

object DummyClient extends Client {

  val hashMap = new ConcurrentHashMap[String, Int]

  override def init() = hashMap.put("key", 0)
  override def read(key: String) = hashMap.get(key)
  override def write(key: String, value: Int) = hashMap.put(key, value)
  override def delete(key: String) = hashMap.remove(key)
  override def terminate() = Unit
}