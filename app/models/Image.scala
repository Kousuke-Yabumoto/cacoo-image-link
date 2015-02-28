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
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat
import java.util.Locale

case class Diagram(diagramId: String, ownerNickname: String, title: String, updated: String)
case class Sheet(name: String, uid: String, imageUrl: String)
case class Image(user: String, diagramId: String, sheetId: String, ownerNickname: String, diagramTitle: String, sheetName: String, imageUrl: String, key: String)

object Image {
  
  def IMAGE_SAVE_PATH(user: String, diagramId: String, sheetId: String) = s"/var/cacoo/cache/${user}-${diagramId}-${sheetId}.png"

  object diagramSeqParser {
    implicit val parser: ResponseParser[Seq[Diagram]] = ResponseParser { js =>
      if (js.result.toString == "undefined") {
        val diagram: Diagram = js.as[Diagram]
        Seq(diagram)
      } else {
        val diagrams: Seq[Diagram] = js.result.as[Seq[Diagram]]
        diagrams
      }
    }
  }
  
  object diagramParser {
    implicit val parser: ResponseParser[Diagram] = ResponseParser { js =>
      val diagram: Diagram = js.as[Diagram]
      diagram
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
  def diagramSeq(user: String, diagramId: Option[String] = None): Future[Seq[Diagram]] = {
    import diagramSeqParser._
    diagramId.fold(Cacoo(user).callJson("diagrams.json")()) { id =>
      Cacoo(user).callJson(s"diagrams/$id.json")()
    }
  }
  
  /**
   * 図の情報を取得する
   */
  def diagram(user: String, diagramId: String): Future[Diagram] = {
    import diagramParser._
    Cacoo(user).callJson(s"diagrams/${diagramId}.json")()
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
  def list(user: String, diagramId: Option[String]): Future[Seq[Image]] = diagramSeq(user, diagramId) map { diagrams =>
    diagrams flatMap { diagram =>
      val futureImages: Future[Seq[Sheet]] = sheetSeq(user, diagram.diagramId)
      val sheets = Await.result(futureImages, Duration.Inf)
      sheets map { sheet =>
        Image(user, diagram.diagramId, sheet.uid, diagram.ownerNickname, diagram.title, sheet.name, s"/image/show/${user}/${diagram.diagramId}/${sheet.uid}", s"${diagram.diagramId}-${sheet.uid}")
      }
    }
  }
  
  /**
   * Cacooを毎回見に行かず、有効期限確認もせずに画像を取得する
   */
  def previewImage(user: String, diagramId: String, sheetId: String) = {
    Future { 
      new File(IMAGE_SAVE_PATH(user, diagramId, sheetId))
    } filter { info => 
      // ファイルが存在する
      info.exists()
    } map { info => 
      Source.fromFile(info)(Codec.ISO8859).map(_.toByte).toArray
    } recoverWith {
      // ファイルが無かったら更新
      case ex: NoSuchElementException => updateImage(user, diagramId, sheetId)
    }
  }
  
  /**
   * 画像を取得する
   */
  def getImage(user: String, diagramId: String, sheetId: String) = {
    diagram(user, diagramId) map { diagram => 
      val file = new File(IMAGE_SAVE_PATH(user, diagramId, sheetId))
      val updated = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z").withLocale(Locale.ENGLISH).parseDateTime(diagram.updated).getMillis()
      (file, updated)
    } recover {
      // Cacooから取得出来ない場合は無視して現在のものを出す
      case e: Throwable => (new File(IMAGE_SAVE_PATH(user, diagramId, sheetId)), 0L)
    } filter { info =>
      // ファイルの更新が無い
      info._2 <= info._1.lastModified()
    } filter { info => 
      // ファイルが存在する
      info._1.exists()
    } filter { info => 
      // 有効期限（１ヶ月）以上前のファイルじゃない
      DateTime.now().minusMonths(1).getMillis() < info._1.lastModified()
    } map { info => 
      Source.fromFile(info._1)(Codec.ISO8859).map(_.toByte).toArray
    } recoverWith {
      // ファイルが無かったり有効期限が切れたりしてたら更新
      case ex: NoSuchElementException => updateImage(user, diagramId, sheetId)
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