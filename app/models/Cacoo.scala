package models

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.libs.ws._
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder
import rapture._
import json._
import jsonBackends.play._
import models.conf.AppConfig
import scala.util.control.NonFatal

/**
 * CacooAPIエントリポイント
 */
object Cacoo {
  
  val BaseUrl = "https://cacoo.com/api/v1/"
  val RetryCount = 10
  
  def apply(user: String): Cacoo = {
    val apiKey = AppConfig.cacoo.user(user).apiKey
    new Cacoo(apiKey.getOrElse(throw new Exception(s"apiキーが設定されていません：$user")))
  }
}

/**
 * レスポンスをパースする
 */
class ResponseParser[A](parseFunc: Json => A) {
  def check(response: WSResponse): Unit = Unit
  def parse(js: Json): A = parseFunc(js)
}
object ResponseParser {
  def apply[A](parseFunc: Json => A) = new ResponseParser(parseFunc)
}

/**
 * API例外
 */
class CacooException(code: Int, body: String) extends Exception(body) {
  def this(msg: String) = this(0, msg)
}

/**
 * CacooAPIへアクセスするクラス
 */
class Cacoo private[Cacoo](apiKey: String) {
  
  /**
   * APIをコールし、JSONを取得する
   */
  def callJson[A](path: String)(params: (String, String)*)(implicit parser: ResponseParser[A]): Future[A] = {
    call(path)(params: _*) map { response =>
      // パーサー別のチェック処理
      parser.check(response)
      response
    } map { response => 
      // 変換処理
      val js = Json.parse(response.body)
      parser.parse(js)
    }
  }
  
  /**
   * APIをコールする
   */
  def call(path: String)(params: (String, String)*): Future[WSResponse] = withRetry(0) { () => 
    val apiParams = ("apiKey" -> apiKey) +: params
    val request = WS.url(s"${Cacoo.BaseUrl}${path}").withQueryString(apiParams: _*)
    
    // レスポンスの処理
    request.get() map { response =>
      // 通常のステータスチェック
      response match {
        case r if (200 > r.status || r.status >= 400) =>
          throw new CacooException(r.status, r.body)
        case r => r
      }
    }
  }
  
  /**
   * リトライ処理
   */
  def withRetry(count: Int)(action:() => Future[WSResponse]): Future[WSResponse] = {
    val result = action()
    result recoverWith {
      case NonFatal(ex) => {
        val newCount = count + 1
        if (newCount <= Cacoo.RetryCount) withRetry(newCount)(action)
        else result
      }
    }
  }
}