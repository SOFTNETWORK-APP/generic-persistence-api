package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.SessionData
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait CookieSessionTestKit[T <: SessionData] extends SessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  def cookieConfig: CookieConfig

  final override val sessionHeaderName: String = cookieConfig.name

}
