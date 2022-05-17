package app.softnetwork.resource.utils

import app.softnetwork.resource.config.Settings._

import java.net.URLEncoder

/**
  * Created by smanciot on 08/07/2018.
  */
object ResourceTools {

  def resourceUri(uuid: String*) = s"$BaseUrl/$ResourcePath/${URLEncoder.encode(uuid.mkString("#"), "UTF-8")}"

  def imageUri(uuid: String*) = s"$BaseUrl/$ResourcePath/images/${URLEncoder.encode(uuid.mkString("#"), "UTF-8")}"

}
