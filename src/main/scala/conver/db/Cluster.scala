package conver.db

trait Cluster {
  def start(num: Int): Array[String]
  def stop(cIds: Array[String])
}