package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheck
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffSessionTestKit extends SessionTestKit with SessionServiceEndpointsRoutes {
  _: Suite with CsrfCheck =>

  override val refreshableSession: Boolean = false

  override def extractSession(value: Option[String]): Option[Session] =
    value match {
      case Some(value) => sessionManager.clientSessionManager.decode(value).toOption
      case _           => None
    }

}
