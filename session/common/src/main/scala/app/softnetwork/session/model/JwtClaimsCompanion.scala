package app.softnetwork.session.model

import app.softnetwork.persistence.generateUUID
import com.softwaremill.session.{SessionConfig, SessionResult}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.softnetwork.session.model.JwtClaims

import java.util.Base64
import scala.util.Try

trait JwtClaimsCompanion extends SessionDataCompanion[JwtClaims] with JwtClaimsKeys {
  override def newSession: JwtClaims = JwtClaims.defaultInstance

  override def apply(s: String): JwtClaims =
    Try {
      val sCleaned = if (s.startsWith("Bearer")) s.substring(7).trim else s
      val List(_, p, _) = sCleaned.split("\\.").toList
      val decodedValue = Try {
        parse(new String(Base64.getUrlDecoder.decode(p), "utf-8"))
      }
      for (jv <- decodedValue) yield {
        val iss = jv \\ "iss" match {
          case JString(value) => Some(value)
          case _              => None
        }
        val sub = jv \\ "sub" match {
          case JString(value) => Some(value)
          case _              => None
        }
        val aud = jv \\ "aud" match {
          case JString(value) => Some(value)
          case _              => None
        }
        val exp = jv \\ "exp" match {
          case JInt(value) => Some(value.toLong)
          case _           => None
        }
        val nbf = jv \\ "nbf" match {
          case JInt(value) => Some(value.toLong)
          case _           => None
        }
        val iat = jv \\ "iat" match {
          case JInt(value) => Some(value.toLong)
          case _           => None
        }
        val jti = jv \\ "jti" match {
          case JString(value) => Some(value)
          case _              => None
        }
        JwtClaims(
          Map.empty,
          _refreshable,
          iss,
          sub,
          aud,
          exp,
          nbf,
          iat,
          jti
        ).withId(sub.getOrElse(generateUUID()))
      }
    }.flatten.toOption.getOrElse(JwtClaims())

  def decode(s: String, clientSecret: String): Option[JwtClaims] = {
    val config = SessionConfig.default(clientSecret)
    JwtClaimsEncoder
      .decode(s, config)
      .map { dr =>
        val expired = config.sessionMaxAgeSeconds.fold(false)(_ =>
          System.currentTimeMillis() > dr.expires.getOrElse(Long.MaxValue)
        )
        if (expired) {
          SessionResult.Expired
        } else if (!dr.signatureMatches) {
          SessionResult.Corrupt(new RuntimeException("Corrupt signature"))
        } else if (dr.isLegacy) {
          SessionResult.DecodedLegacy(dr.t)
        } else {
          SessionResult.Decoded(dr.t)
        }
      }
      .recover { case t: Exception => SessionResult.Corrupt(t) }
      .get match {
      case SessionResult.Decoded(claims) => Some(claims)
      case _                             => None
    }
  }

}
