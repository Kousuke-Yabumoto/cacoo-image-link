package models.conf

import scala.language.dynamics 
import play.api._
import com.typesafe.config._

/**
 * 設定ファイルから値を取得する
 * 「AppConfig.cacoo.yabumoto.apiKey」とかで書ける
 * 「AppConfig.cacoo("yabumoto").apiKey」とでも書ける
 */
object AppConfig extends Dynamic {
  
  val config = Configuration(ConfigFactory.load())
  
  def apply(key: String*): AppConfig = new AppConfig(key: _*)
  def selectDynamic(key: String): AppConfig = new AppConfig(key)
  def applyDynamic(key: String)(index: String): AppConfig = new AppConfig(key, index)
}

class AppConfig private[conf](val keys: String*) extends Dynamic {
  
  def apply(key: String*): AppConfig = {
    val newKeys = keys ++ key
    new AppConfig(newKeys: _*)
  }
  
  def get: Option[String] = AppConfig.config.getString(keys.mkString("."))
  def getOrElse[B >: String](default: => B): B = AppConfig.config.getString(keys.mkString(".")).getOrElse(default)
  def selectDynamic(key: String): AppConfig = new AppConfig((keys :+ key): _*)
  def applyDynamic(key: String)(index: String): AppConfig = new AppConfig((keys :+ key :+ index): _*)
}