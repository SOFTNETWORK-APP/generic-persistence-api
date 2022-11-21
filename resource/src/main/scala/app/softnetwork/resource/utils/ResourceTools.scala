package app.softnetwork.resource.utils

import app.softnetwork.resource.config.Settings._

import java.net.URLEncoder

/**
  * Created by smanciot on 08/07/2018.
  */
object ResourceTools {

  def resourceUri(uuid: String*): String =
    s"$BaseUrl/$ResourcePath/${uuid.map(URLEncoder.encode(_, "UTF-8")).mkString("/")}"

  def imageUri(uuid: String*): String = resourceUri((Seq("images") ++ uuid): _*)

  def libraryUri(uuid: String*): String = resourceUri((Seq("library") ++ uuid): _*)

}
