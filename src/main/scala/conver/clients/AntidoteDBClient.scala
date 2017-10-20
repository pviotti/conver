package conver.clients

import eu.antidotedb.client.AntidoteClient
import eu.antidotedb.client.Bucket
import eu.antidotedb.client.Host
import scala.util.Random
import com.typesafe.scalalogging.LazyLogging
import eu.antidotedb.client.RegisterRef
import eu.antidotedb.client.ValueCoder

class AntidoteDBClient extends Client with LazyLogging {

  var adb = null: AntidoteClient
  var regRef = null: RegisterRef[String]

  val tstBucket = "tstBucket"
  val KEY = "tstInt"

  def init(connStr: String) = {
    try {
      val port = Random.shuffle(connStr.split(",").toList).head
      adb = new AntidoteClient(new Host("127.0.0.1", port.toInt))
      logger.info("Client connected to 127.0.0.1:" + port)
      val bucket: Bucket[String] = Bucket.create(tstBucket);
      regRef = bucket.register(KEY, ValueCoder.utf8String)
      regRef.set(adb.noTransaction(), Client.INIT_VALUE.toString())
    } catch {
      case e: Exception => throw e
    }
    this
  }

  def read(key: String) = {
    try {
      // logger.debug("Reading " + key)
      regRef.read(adb.noTransaction()).toInt
    } catch {
      case e: Exception => throw e
    }
  }

  def write(key: String, value: Int) = {
    try {
      // logger.debug("Writing " + value)
      regRef.set(adb.noTransaction(), value.toString())
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
