package app.softnetwork.session.model

import app.softnetwork.serialization.commonFormats
import app.softnetwork.session.config.Settings.Session.DefaultSessionConfig
import org.json4s.{Formats, JValue}
import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.session.model.Session

class SessionSerializersSpec extends AnyWordSpecLike {

  implicit def formats: Formats = commonFormats

  val session: Session = Session.defaultInstance.withKvs(
    Map(
      "_sessiondata" -> "id",
      "profile"      -> "profile",
      "admin"        -> "true"
    )
  )

  var result: String = _

  var jresult: JValue = _

  "basic session serializer" must {
    "serialize" in {
      result = SessionSerializers.basic(DefaultSessionConfig.serverSecret).serialize(session)
    }
    "deserialize" in {
      check(
        SessionSerializers.basic(DefaultSessionConfig.serverSecret).deserialize(result).toOption
      )
    }
  }

  "jwt session serializer" must {
    "serialize" in {
      jresult = SessionSerializers.jwt.serialize(session)
    }
    "deserialize" in {
      check(SessionSerializers.jwt.deserialize(jresult).toOption)
    }
  }

  def check(maybeSession: Option[Session]): Assertion = {
    assert(maybeSession.isDefined)
    assert(maybeSession.get.id == session.id)
    assert(maybeSession.get.profile.getOrElse("") == session.profile.getOrElse(""))
    assert(maybeSession.get.admin == session.admin)
  }
}
