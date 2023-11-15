package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.{Logger, LoggerFactory}

trait SessionTestKitSpec extends AnyWordSpecLike with SessionTestKit {
  _: ApiRoutes with SessionMaterials =>

  override lazy val log: Logger = LoggerFactory getLogger getClass.getName

  val id: String = "id"

  val profile: String = "profile"

  val admin: Boolean = true

  "Session" must {
    "be created" in {
      createSession(id, Some(profile), Some(admin))
    }
    "be extracted" in {
      val session = extractSession()
      assert(session.isDefined)
      assert(session.map(_.id).getOrElse("") == id)
      assert(session.flatMap(_.profile).getOrElse("") == profile)
      assert(session.map(_.admin).getOrElse(!admin) == admin)
    }
    "be invalidated" in {
      invalidateSession()
      assert(extractSession(false).isEmpty)
    }
  }

}
