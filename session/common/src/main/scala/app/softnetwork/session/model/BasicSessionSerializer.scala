package app.softnetwork.session.model

import app.softnetwork.session.security.Crypto
import com.softwaremill.session.SessionSerializer

import scala.language.reflectiveCalls
import scala.util.Try

class BasicSessionSerializer[T <: SessionData](val serverSecret: String)(implicit
  companion: SessionDataCompanion[T]
) extends SessionSerializer[T, String] {

  override def serialize(session: T): String = {
    val encoded = java.net.URLEncoder
      .encode(
        session.data
          .filterNot(_._1.contains(":"))
          .map(d => d._1 + ":" + d._2)
          .mkString("\u0000"),
        "UTF-8"
      )
    Crypto.sign(encoded, serverSecret) + "-" + encoded
  }

  override def deserialize(r: String): Try[T] = {
    def urldecode(data: String): Map[String, String] =
      Map[String, String](
        java.net.URLDecoder
          .decode(data, "UTF-8")
          .split("\u0000")
          .map(_.split(":"))
          .map(p => p(0) -> p.drop(1).mkString(":")): _*
      )

    // Do not change this unless you understand the security issues behind timing attacks.
    // This method intentionally runs in constant time if the two strings have the same length.
    // If it didn't, it would be vulnerable to a timing attack.
    def safeEquals(a: String, b: String): Boolean = {
      if (a.length != b.length) false
      else {
        var equal = 0
        for (i <- Array.range(0, a.length)) {
          equal |= a(i) ^ b(i)
        }
        equal == 0
      }
    }

    Try {
      val split = r.split("-")
      val message = split.tail.mkString("-")
      if (safeEquals(split(0), Crypto.sign(message, serverSecret)))
        companion.newSession.withData(urldecode(message))
      else
        throw new Exception("corrupted encrypted data")
    }
  }
}
