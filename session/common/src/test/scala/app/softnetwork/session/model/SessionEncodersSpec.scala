package app.softnetwork.session.model

import app.softnetwork.serialization.commonFormats
import app.softnetwork.session.config.Settings.Session.DefaultSessionConfig
import com.softwaremill.session.{
  JwtSessionEncoder,
  SessionConfig,
  SessionEncoder,
  SessionSerializer
}
import org.json4s.{Formats, JValue}
import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.session.model.Session

class SessionEncodersSpec extends AnyWordSpecLike {

  implicit def sessionConfig: SessionConfig = DefaultSessionConfig

  implicit def formats: Formats = commonFormats

  val session: Session = Session.defaultInstance.withKvs(
    Map(
      "_sessiondata" -> "id",
      "profile"      -> "profile",
      "admin"        -> "true"
    )
  )

  val now: Long = System.currentTimeMillis()

  var result: String = _

  "basic session encoder" must {
    "encode" in {
      implicit val serializer: SessionSerializer[Session, String] =
        SessionSerializers.basic(sessionConfig.serverSecret)
      result = SessionEncoder.basic.encode(session, now, sessionConfig)
    }
    "decode" in {
      implicit val serializer: SessionSerializer[Session, String] =
        SessionSerializers.basic(sessionConfig.serverSecret)
      SessionEncoder.basic[Session].decode(result, sessionConfig).toOption match {
        case Some(r) => check(Some(r.t))
        case _       => fail("unable to decode session")
      }
    }
  }

  "jwt session encoder" must {
    "encode" in {
      implicit val serializer: SessionSerializer[Session, JValue] = SessionSerializers.jwt(formats)
      result = new JwtSessionEncoder[Session].encode(session, now, sessionConfig)
    }
    "decode" in {
      implicit val serializer: SessionSerializer[Session, JValue] = SessionSerializers.jwt(formats)
      new JwtSessionEncoder[Session].decode(result, sessionConfig).toOption match {
        case Some(r) => check(Some(r.t))
        case _       => fail("unable to decode session")
      }
    }
  }

  def check(maybeSession: Option[Session]): Assertion = {
    assert(maybeSession.isDefined)
    assert(maybeSession.get.id == session.id)
    assert(maybeSession.get.profile.getOrElse("") == session.profile.getOrElse(""))
    assert(maybeSession.get.admin == session.admin)
  }
}
