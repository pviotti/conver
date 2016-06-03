package conver.clients

trait Client {
  def init: Any
  def read(key: String): Int
  def write(key: String, value: Int): Any
  def delete(key: String): Any
  def terminate: Any
}

object Client {
  val INIT_VALUE = 0
  val KEY = "k"
}