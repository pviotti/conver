package conver.clients

import eu.antidotedb.client.AntidoteClient
import eu.antidotedb.client.Bucket
import eu.antidotedb.client.Host
import eu.antidotedb.client.SetRef
import eu.antidotedb.client.CounterRef
import eu.antidotedb.client.IntegerRef
import scala.util.Random
import com.typesafe.scalalogging.LazyLogging

class AntidoteDBClient extends Client with LazyLogging {

  var adb = null: AntidoteClient
  var intRef = null: IntegerRef

  val tstBucket = "tstBucket"
  val KEY = "tstInt"

  def init(connStr: String) = {
    try {
      val port = Random.shuffle(connStr.split(",").toList).head
      logger.info("Client connecting to 127.0.0.1:" + port)
      adb = new AntidoteClient(new Host("127.0.0.1", port.toInt))
      val bucket: Bucket[String] = Bucket.create("tstBucket");
      intRef = bucket.integer(KEY)
      intRef.set(adb.noTransaction(), Client.INIT_VALUE)
    } catch {
      case e: Exception => throw e
    }
    this
  }

  def read(key: String) = {
    try {
      // logger.debug("Reading " + key)
      intRef.read(adb.noTransaction()).toInt
    } catch {
      case e: Exception => throw e
    }
  }

  def write(key: String, value: Int) = {
    try {
      // logger.debug("Writing " + value)
      intRef.set(adb.noTransaction(), value)
    } catch {
      case e: Exception => throw e
    }
  }

  def delete(key: String) = Unit

  def terminate = Unit
}

//object AntidoteClientObject {
//  def main(arg: Array[String]): Unit = {
//    // docker run -d --name antidote -p "8087:8087" mweber/antidotedb
//    val a = new AntidoteDBClient().init("8089,8087,8088")
//    val b = new AntidoteDBClient().init("8089,8087,8088")
//    val c = new AntidoteDBClient().init("8089,8087,8088")
//    val d = new AntidoteDBClient().init("8089,8087,8088")
//    val e = new AntidoteDBClient().init("8089,8087,8088")
//    a.write("key", 42)
//    c.write("key", 23)
//    b.write("key", 21)
//    println(b.read("key"))
//    d.write("key", 110)
//    b.write("key", 25)
//    println(c.read("key"))
//    b.write("key", 21)
//    a.write("key", 34)
//    e.write("key", 28)
//    println(c.read("key"))
//  }
//}
