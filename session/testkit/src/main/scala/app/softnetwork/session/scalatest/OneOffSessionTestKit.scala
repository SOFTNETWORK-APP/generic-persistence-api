package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes with SessionMaterials =>

  override val refreshableSession: Boolean = false

  override def extractSession(value: Option[String]): Option[Session] =
    value match {
      case Some(value) => manager.clientSessionManager.decode(value).toOption
      case _           => None
    }

}
