package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait RefreshableCookieSessionTestKit extends CookieSessionTestKit with RefreshableSessionTestKit {
  _: Suite with ApiRoutes with SessionMaterials =>

  override def cookieConfig: CookieConfig = manager.config.refreshTokenCookieConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.RefreshableCookie

}
