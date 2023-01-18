package app.softnetwork.utils

import org.apache.tika.Tika

import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import scala.util.{Failure, Success, Try}

/** Created by smanciot on 06/07/2018.
  */
object MimeTypeTools {

  val FORMAT: Pattern = Pattern.compile("(.*)\\/(.*)")

  def detectMimeType(bytes: Array[Byte]): Option[String] = {
    Try(new Tika().detect(bytes)) match {
      case Success(s) => Some(s)
      case Failure(_) => None
    }
  }

  def detectMimeType(path: Path): Option[String] = {
    if (Files.exists(path)) {
      Try(new Tika().detect(path)) match {
        case Success(s) => Some(s)
        case Failure(_) => None
      }
    } else {
      None
    }
  }

  def toFormat(path: Path): Option[String] = {
    toFormat(detectMimeType(path))
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
