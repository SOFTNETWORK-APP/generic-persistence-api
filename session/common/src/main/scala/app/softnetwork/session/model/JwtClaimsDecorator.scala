package app.softnetwork.session.model

import com.softwaremill.session.SessionConfig
import org.softnetwork.session.model.JwtClaims

trait JwtClaimsDecorator extends SessionDataDecorator[JwtClaims] with JwtClaimsKeys {
  self: JwtClaims =>

  lazy val data: Map[String, String] = additionalClaims

  override def withData(data: Map[String, String]): JwtClaims = withAdditionalClaims(data)

  override def withId(id: String): JwtClaims =
    synchronized {
      dirty = true
      withData(data + (idKey -> id)).withSub(id)
    }

  override lazy val clientId: Option[String] = iss.orElse(get(clientIdKey))

  override def withClientId(clientId: String): JwtClaims =
    synchronized {
      dirty = true
      withData(data + (clientIdKey -> clientId)).withIss(clientId)
    }

  def encode(clientId: String, clientSecret: String): String = {
    JwtClaimsEncoder.encode(
      this.withIss(clientId),
      System.currentTimeMillis(),
      SessionConfig.default(clientSecret)
    )
  }

  lazy val issuer: Option[String] = iss.orElse(clientId)

  lazy val subject: Option[String] = sub.orElse(get(idKey))
}
