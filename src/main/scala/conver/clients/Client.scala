package conver.clients

trait Client {
  def init(connStr: String): Any
  def read(key: String): Int
  def write(key: String, value: Int): Any
  def delete(key: String): Any
  def terminate: Any

  override def toString = this.getClass.getSimpleName
}

object Client {
  val INIT_VALUE = 0
  val KEY = "k"
}