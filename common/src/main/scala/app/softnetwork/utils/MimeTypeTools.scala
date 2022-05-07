package app.softnetwork.utils

import org.apache.tika.Tika

import java.io.File
import java.util.regex.Pattern
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 06/07/2018.
  */
object MimeTypeTools {

  val FORMAT: Pattern = Pattern.compile("(.*)\\/(.*)")

  def detectMimeType(file: File): Option[String] = {
    if (Option(file).isDefined && file.exists()) {
      Try(new Tika().detect(file)) match {
        case Success(s) => Some(s)
        case Failure(f) => None
      }
    } else {
      None
    }
  }

  def toFormat(file: File): Option[String] = {
    toFormat(detectMimeType(file))
  }

  def toFormat(mimeType: Option[String]): Option[String] = {
    mimeType match {
      case Some(s) =>
        val matcher = FORMAT.matcher(s)
        if (matcher.find() && matcher.groupCount() > 1) {
          Some(matcher.group(2))
        } else {
          None
        }
      case None => None
    }
  }

}
