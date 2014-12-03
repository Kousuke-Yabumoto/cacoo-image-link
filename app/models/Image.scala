package models

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import rapture._
import json._
import play.api.libs.ws.ning._
import java.io._
import scala.io._
import org.joda.time.DateTime

case class Diagram(diagramId: String, ownerNickname: String, title: String)
case class Sheet(name: String, uid: String, imageUrl: String)
case class Image(user: String, diagramId: String, sheetId: String, ownerNickname: String, diagramTitle: String, sheetName: String, imageUrl: String, key: String)

object Image {
  
  def IMAGE_SAVE_PATH(user: String, diagramId: String, sheetId: String) = s"/var/cacoo/cache/${user}-${diagramId}-${sheetId}.png"

  object diagramParser {
    implicit val parser: ResponseParser[Seq[Diagram]] = ResponseParser { js =>
      val diagrams: Seq[Diagram] = js.result.as[Seq[Diagram]]
      diagrams
    }
  }

  object imageParser {
    implicit val parser: ResponseParser[Seq[Sheet]] = ResponseParser { js =>
      val images: Seq[Sheet] = js.sheets.as[Seq[Sheet]]
      images
    }
  }

  /**
   * 図の一覧を取得する
   */
  def diagramSeq(user: String): Future[Seq[Diagram]] = {
    import diagramParser._
    Cacoo(user).callJson("diagrams.json")()
  }

  /**
   * シート一覧を取得する
   */
  def sheetSeq(user: String, diagramId: String): Future[Seq[Sheet]] = {
    import imageParser._
    Cacoo(user).callJson(s"diagrams/${diagramId}.json")()
  }

  /**
   * Imageリストを取得する
   */
  def list(user: String): Future[Seq[Image]] = diagramSeq(user) map { diagrams =>
    diagrams flatMap { diagram =>
      val futureImages: Future[Seq[Sheet]] = sheetSeq(user, diagram.diagramId)
      val sheets = Await.result(futureImages, Duration.Inf)
      sheets map { sheet =>
        Image(user, diagram.diagramId, sheet.uid, diagram.ownerNickname, diagram.title, sheet.name, s"/image/show/${user}/${diagram.diagramId}/${sheet.uid}", s"${diagram.diagramId}-${sheet.uid}")
      }
    }
  }
  
  /**
   * 画像を取得する
   */
  def getImage(user: String, diagramId: String, sheetId: String) = {
    future {
      val file = new File(IMAGE_SAVE_PATH(user, diagramId, sheetId))
      if (file.exists() && DateTime.now().minusMonths(1).getMillis() < file.lastModified()) {
        Source.fromFile(file)(Codec.ISO8859).map(_.toByte).toArray
      } else {
        throw new IOException("ファイルが存在しないか有効期限が切れています")
      }
    } recoverWith {
      case ex: IOException => updateImage(user, diagramId, sheetId)
    }
  }
  
  /**
   * ファイルに保存
   */
  def updateImage(user: String, diagramId: String, sheetId: String): Future[Array[Byte]] = downloadImage(user, diagramId, sheetId) map { image => 
    val file = new BufferedOutputStream(new FileOutputStream(IMAGE_SAVE_PATH(user, diagramId, sheetId)))
    try{
      file.write(image)
    } finally {
      file.close()
    }
    image
  }
  
  /**
   * API経由でファイルダウンロード
   */
  def downloadImage(user: String, diagramId: String, sheetId: String) = {
    Cacoo(user).call(s"diagrams/${diagramId}-${sheetId}.png")() map { response => 
      val r = response.asInstanceOf[NingWSResponse]
      r.ahcResponse.getResponseBodyAsBytes 
    }
  }
}