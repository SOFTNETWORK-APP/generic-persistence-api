package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.CookieConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait RefreshableCookieSessionTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends CookieSessionTestKit[T]
    with RefreshableSessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override lazy val cookieConfig: CookieConfig = manager.config.refreshTokenCookieConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.RefreshableCookie

}
