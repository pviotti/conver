package conver.clients

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.NodeExistsException
import java.nio.ByteBuffer
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher.Event.KeeperState
import java.util.concurrent.CountDownLatch
import java.nio.BufferUnderflowException

class ZkClient extends Client {

  var zk = null: ZooKeeper

  val tstPath = "/test"

  def init(connStr: String) = {
    try {
      println("Client connecting to " + connStr)
      val connSignal = new CountDownLatch(1)
      zk = new ZooKeeper(connStr, 3000, new Watcher() {
        def process(we: WatchedEvent) = {
          if (we.getState == KeeperState.SyncConnected)
            connSignal.countDown()
        }
      })
      connSignal.await()
      zk.create(tstPath,
                ByteBuffer.allocate(32).putInt(Client.INIT_VALUE).array(),
                Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT)
    } catch {
      case e: NodeExistsException => ;
      case e: Exception => throw e
    }
    this
  }

  def read(key: String) = {
    try {
      ByteBuffer.wrap(zk.getData(tstPath, false, null)).getInt
    } catch {
      case e: BufferUnderflowException => -1
      case e: Exception => throw e
    }
  }

  def write(key: String, value: Int) =
    zk.setData(tstPath, ByteBuffer.allocate(32).putInt(value).array(), -1)

  def delete(key: String) =
    zk.delete(tstPath, -1)

  def terminate =
    zk.close()

  //  def main(arg: Array[String]): Unit = {
  //    val a = init("172.18.0.2:2181,172.18.0.3:2181,172.18.0.4:2181")
  //    a.write("key", 1)
  //    println(a.read("key"))
  //  }
}
