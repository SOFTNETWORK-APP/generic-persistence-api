package app.softnetwork.session.model

import app.softnetwork.session.config.Settings.Session.DefaultSessionConfig
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.session.model.JwtClaims

class JwtClaimsSpec extends AnyWordSpecLike {

  val session: JwtClaims =
    JwtClaims.newSession /*.withId("id")*/.withProfile("profile").withAdmin(true)

  val clientId = "clientId"

  val clientSecret: String = DefaultSessionConfig.serverSecret

  var result: String = _

  "jwt claims" must {
    "encode" in {
      result = session.encode(clientId, clientSecret)
      assert(result.nonEmpty)
    }
    "decode" in {
      JwtClaims.decode(result, clientSecret) match {
        case Some(r) =>
          assert(r.clientId.isDefined)
          assert(r.clientId.get == clientId)
          assert(r.iss.isDefined)
          assert(r.iss.get == clientId)
          assert(r.profile.isDefined)
          assert(r.profile.get == session.profile.get)
          assert(r.admin == session.admin)
          assert(r.sub.isDefined)
          assert(r.id == r.sub.get)
        case _ => fail("unable to decode session")
      }
    }
  }
}
