package app.softnetwork.session.model

import com.softwaremill.session.SessionSerializer
import org.json4s.JValue
import org.softnetwork.session.model.Session

object SessionSerializers {

  def basic[T <: SessionData](secret: String)(implicit
    companion: SessionDataCompanion[T]
  ): SessionSerializer[T, String] =
    new BasicSessionSerializer[T](secret)

  def jwt[T <: SessionData](implicit
    companion: SessionDataCompanion[T]
  ): SessionSerializer[T, JValue] =
    new JwtSessionSerializer[T]
}
