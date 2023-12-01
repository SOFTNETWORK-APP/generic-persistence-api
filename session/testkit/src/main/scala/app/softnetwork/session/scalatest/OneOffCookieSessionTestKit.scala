package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.SessionData
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffCookieSessionTestKit[T <: SessionData]
    extends CookieSessionTestKit[T]
    with OneOffSessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override def cookieConfig: CookieConfig = manager.config.sessionCookieConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.OneOffCookie

}
