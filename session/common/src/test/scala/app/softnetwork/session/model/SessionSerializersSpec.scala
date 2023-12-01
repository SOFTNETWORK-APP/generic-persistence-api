package app.softnetwork.session.model

import app.softnetwork.session.config.Settings.Session.DefaultSessionConfig
import org.json4s.JValue
import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.session.model.Session

class SessionSerializersSpec extends AnyWordSpecLike {

  val session: Session = Session.newSession.withId("id").withProfile("profile").withAdmin(true)

  var result: String = _

  var jresult: JValue = _

  "basic session serializer" must {
    "serialize" in {
      result =
        SessionSerializers.basic[Session](DefaultSessionConfig.serverSecret).serialize(session)
    }
    "deserialize" in {
      check(
        SessionSerializers
          .basic[Session](DefaultSessionConfig.serverSecret)
          .deserialize(result)
          .toOption
      )
    }
  }

  "jwt session serializer" must {
    "serialize" in {
      jresult = SessionSerializers.jwt[Session].serialize(session)
    }
    "deserialize" in {
      check(SessionSerializers.jwt[Session].deserialize(jresult).toOption)
    }
  }

  def check(maybeSession: Option[Session]): Assertion = {
    assert(maybeSession.isDefined)
    assert(maybeSession.get.id == session.id)
    assert(maybeSession.get.profile.getOrElse("") == session.profile.getOrElse(""))
    assert(maybeSession.get.admin == session.admin)
  }
}
