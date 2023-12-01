package app.softnetwork.session.model

import com.softwaremill.session.{
  JwtSessionEncoder,
  SessionConfig,
  SessionManager,
  SessionSerializer
}
import org.json4s.JValue
import org.softnetwork.session.model.Session

object SessionManagers {

  def basic[T <: SessionData](implicit
    sessionConfig: SessionConfig,
    companion: SessionDataCompanion[T]
  ): SessionManager[T] = {
    implicit val serializer: SessionSerializer[T, String] =
      SessionSerializers.basic[T](sessionConfig.serverSecret)
    new SessionManager[T](sessionConfig)
  }

  def jwt[T <: SessionData](implicit
    sessionConfig: SessionConfig,
    companion: SessionDataCompanion[T]
  ): SessionManager[T] = {
    implicit val serializer: SessionSerializer[T, JValue] = SessionSerializers.jwt[T]
    implicit val encoder: JwtSessionEncoder[T] = new JwtSessionEncoder[T]
    new SessionManager[T](sessionConfig)(encoder)
  }
}
