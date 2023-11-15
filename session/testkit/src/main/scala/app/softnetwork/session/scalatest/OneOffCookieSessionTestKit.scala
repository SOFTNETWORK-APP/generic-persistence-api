package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffCookieSessionTestKit extends CookieSessionTestKit with OneOffSessionTestKit {
  _: Suite with ApiRoutes with SessionMaterials =>

  override def cookieConfig: CookieConfig = manager.config.sessionCookieConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.OneOffCookie

}
