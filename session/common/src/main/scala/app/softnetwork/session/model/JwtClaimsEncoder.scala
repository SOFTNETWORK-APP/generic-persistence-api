package app.softnetwork.session.model

import app.softnetwork.concurrent.Completion
import com.softwaremill.session._
import org.json4s.{DefaultFormats, Formats, JValue}
import org.softnetwork.session.model.{ApiKey, JwtClaims}

import scala.concurrent.Future
import scala.util.Try

trait JwtClaimsEncoder extends SessionEncoder[JwtClaims] with Completion {

  implicit def formats: Formats = DefaultFormats

  implicit def sessionSerializer: SessionSerializer[JwtClaims, JValue] = SessionSerializers.jwt

  def loadApiKey(clientId: String): Future[Option[ApiKey]]

  def sessionEncoder = new JwtSessionEncoder[JwtClaims]

  override def encode(t: JwtClaims, nowMillis: Long, config: SessionConfig): String = {
    val updatedJwtClaims = t.copy(
      iss = t.issuer.orElse(config.jwt.issuer),
      sub = t.subject.orElse(config.jwt.subject),
      aud = t.aud.orElse(config.jwt.audience)
    )
    val jwt = config.jwt.copy(
      issuer = updatedJwtClaims.iss,
      subject = updatedJwtClaims.sub,
      audience = updatedJwtClaims.aud
    )
    (updatedJwtClaims.iss match {
      case Some(iss) =>
        (loadApiKey(iss) complete ()).toOption.flatten
      case _ => None
    }) match {
      case Some(apiKey) if apiKey.clientSecret.isDefined =>
        sessionEncoder.encode(
          updatedJwtClaims,
          nowMillis,
          config.copy(jwt = jwt, serverSecret = apiKey.clientSecret.get)
        )
      case _ => sessionEncoder.encode(updatedJwtClaims, nowMillis, config.copy(jwt = jwt))
    }
  }

  override def decode(s: String, config: SessionConfig): Try[DecodeResult[JwtClaims]] = {
    val jwtClaims = JwtClaims(s)
    val maybeClientId =
      if (jwtClaims.iss.contains(config.jwt.issuer.getOrElse(""))) jwtClaims.sub
      else jwtClaims.iss
    val innerConfig = (maybeClientId match {
      case Some(clientId) =>
        (loadApiKey(clientId) complete ()).toOption.flatten.flatMap(_.clientSecret)
      case _ => None
    }) match {
      case Some(clientSecret) =>
        config.copy(serverSecret = clientSecret)
      case _ =>
        config
    }
    sessionEncoder
      .decode(s, innerConfig)
      .map(result =>
        result.copy(t = jwtClaims.copy(additionalClaims = result.t.data ++ jwtClaims.data))
      )

  }
}

case object JwtClaimsEncoder extends JwtClaimsEncoder {
  override def loadApiKey(clientId: String): Future[Option[ApiKey]] = Future.successful(None)
}
