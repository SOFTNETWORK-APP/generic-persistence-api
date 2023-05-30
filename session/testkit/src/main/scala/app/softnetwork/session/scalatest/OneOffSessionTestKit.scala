package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes =>

  override val refreshableSession: Boolean = false

  override def extractSession(value: Option[String]): Option[Session] =
    value match {
      case Some(value) => sessionManager.clientSessionManager.decode(value).toOption
      case _           => None
    }

}
