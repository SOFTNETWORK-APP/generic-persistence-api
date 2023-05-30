package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait CookieSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes =>

  def cookieConfig: CookieConfig

  final override val sessionHeaderName: String = cookieConfig.name

}
