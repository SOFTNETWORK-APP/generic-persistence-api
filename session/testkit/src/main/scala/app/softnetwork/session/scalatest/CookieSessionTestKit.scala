package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait CookieSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes with SessionMaterials =>

  def cookieConfig: CookieConfig

  final override val sessionHeaderName: String = cookieConfig.name

}
