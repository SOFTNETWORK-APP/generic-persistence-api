package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait OneOffCookieSessionTestKit extends CookieSessionTestKit with OneOffSessionTestKit {
  _: Suite with ApiRoutes =>

  override def cookieConfig: CookieConfig = sessionManager.config.sessionCookieConfig

}
