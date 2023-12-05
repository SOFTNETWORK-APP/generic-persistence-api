package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.{Logger, LoggerFactory}

trait SessionTestKitSpec[T <: SessionData with SessionDataDecorator[T]]
    extends AnyWordSpecLike
    with SessionTestKit[T] {
  _: ApiRoutes with SessionMaterials[T] =>

  override lazy val log: Logger = LoggerFactory getLogger getClass.getName

  val id: String = "id"

  val profile: String = "profile"

  val admin: Boolean = true

  val clientId = "clientId"

  val anonymous: Boolean = true

  "Session" must {
    "be created" in {
      createSession(id, Some(profile), Some(admin), Some(clientId), anonymous)
    }
    "be extracted" in {
      val session = extractSession()
      assert(session.isDefined)
      assert(session.map(_.id).getOrElse("") == id)
      assert(session.flatMap(_.profile).getOrElse("") == profile)
      assert(session.map(_.admin).getOrElse(!admin) == admin)
      assert(session.flatMap(_.clientId).getOrElse("") == clientId)
      assert(session.map(_.anonymous).getOrElse(!anonymous) == anonymous)
    }
    "be invalidated" in {
      invalidateSession()
      assert(extractSession(false).isEmpty)
    }
  }

}
