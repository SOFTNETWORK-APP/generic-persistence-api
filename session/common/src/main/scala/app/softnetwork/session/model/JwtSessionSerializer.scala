package app.softnetwork.session.model

import com.softwaremill.session.SessionSerializer
import org.json4s.jackson.JsonMethods.asJValue
import org.json4s.{DefaultFormats, DefaultWriters, Formats, JValue}

import scala.util.Try

class JwtSessionSerializer[T <: SessionData](implicit companion: SessionDataCompanion[T])
    extends SessionSerializer[T, JValue] {

  implicit val formats: Formats = DefaultFormats

  import DefaultWriters._

  override def serialize(t: T): JValue = asJValue(t.data)

  override def deserialize(r: JValue): Try[T] = Try {
    companion.newSession.withData(r.extract[Map[String, String]])
  }
}
