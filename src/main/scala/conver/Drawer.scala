package conver

import scala.collection.mutable.ListBuffer
import java.awt.Graphics2D
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.File
import java.awt.Font
import java.awt.Color
import scala.collection.immutable.ListMap

object Drawer {

  def drawExecution(
    nClients: Int,
    opLst: ListBuffer[Operation],
    duration: Long): Unit = {

    val sessions: ListMap[Char, ListBuffer[Operation]] =
      ListMap(opLst.groupBy { x => x.proc }.toSeq.sortBy(_._1): _*) // sort sessions' hashmap by ClientId

    val opHeight = 40; val txtHeight = 20; val vMargin = 38; val hMargin = 50
    val goldenRatio = (1 + Math.sqrt(5)) / 2
    val h = (opHeight + vMargin) * nClients * 2
    val w = (h + h / goldenRatio).toInt
    val lineLength = w - (hMargin * 2)

    val bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB) // 8-bit RGBA packed into integer pixels
    val ig2: Graphics2D = bi.createGraphics
    ig2.setColor(Color.WHITE)
    ig2.fillRect(0, 0, w, h)
    ig2.setPaint(Color.BLACK)

    val fnScaleTime = (x: Long) => ((lineLength * x) / duration + hMargin).toInt

    for (((cId, session), i) <- sessions.zipWithIndex) {

      val lineY = (h / (2 * nClients) + i * (h / nClients) + vMargin).toInt
      ig2.drawString(cId.toString.toUpperCase, (hMargin / 2).toInt, lineY)
      ig2.drawLine(hMargin, lineY, w - hMargin, lineY)

      for (op <- session) {
        val opX = fnScaleTime(op.sTime)
        val width = fnScaleTime(op.eTime) - opX
        ig2.drawRect(opX, lineY - opHeight, width, opHeight)
        ig2.drawString(op.toLabelString, opX, lineY + txtHeight)
      }
    }
    ImageIO.write(bi, "PNG", new File("exec.png"))
  }
}