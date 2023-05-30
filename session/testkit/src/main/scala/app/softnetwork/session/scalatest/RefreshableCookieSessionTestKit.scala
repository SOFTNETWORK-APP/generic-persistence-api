package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait RefreshableCookieSessionTestKit extends CookieSessionTestKit with RefreshableSessionTestKit {
  _: Suite with ApiRoutes =>

  override def cookieConfig: CookieConfig = sessionManager.config.refreshTokenCookieConfig

}
