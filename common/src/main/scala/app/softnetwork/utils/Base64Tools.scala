package app.softnetwork.utils

import org.apache.commons.codec.binary.Base64

import java.util.regex.Pattern

/**
  * Created by smanciot on 06/07/2018.
  */
object Base64Tools {

  val DATA_ENCODED = Pattern.compile("data:(.*);base64,(.*)")

  def encodeBase64(bytes: Array[Byte], mimeType: Option[String] = None): String = {
    val encoded = Base64.encodeBase64String(bytes)
    mimeType match {
      case Some(s) => s"data:$mimeType;base64,$encoded"
      case _ => encoded
    }
  }

  def decodeBase64(encoded: String) = {
    val matcher = DATA_ENCODED.matcher(encoded)
    var base64 = false
    val ret =
      if (matcher.find() && matcher.groupCount() > 1) {
        base64 = true
        matcher.group(2)
      }
      else {
        encoded
      }
    if (base64 || Base64.isBase64(ret)) {
      Base64.decodeBase64(ret)
    }
    else {
      ret.getBytes("UTF-8")
    }
  }
}
