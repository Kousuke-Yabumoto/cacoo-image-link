package models

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import rapture._
import json._

import play.api.libs.ws.ning._

case class Diagram(diagramId: String, ownerNickname: String, title: String)
case class Sheet(name: String, uid: String, imageUrl: String)
case class Image(ownerNickname: String, diagramTitle: String, sheetName: String, imageUrl: String)

object Image {

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
        Image(diagram.ownerNickname, diagram.title, sheet.name, s"/image/${user}/${diagram.diagramId}/${sheet.uid}")
      }
    }
  }
  
  def getImage(user: String, diagramId: String, sheetId: String) = {
    Cacoo(user).call(s"diagrams/${diagramId}-${sheetId}.png")() map { response => 
      val r = response.asInstanceOf[NingWSResponse]
      r.ahcResponse.getResponseBodyAsBytes 
    }
  }
}