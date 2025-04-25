package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait CookieSessionTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends SessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  def cookieConfig: CookieConfig

  final override lazy val sessionHeaderName: String = cookieConfig.name

}
