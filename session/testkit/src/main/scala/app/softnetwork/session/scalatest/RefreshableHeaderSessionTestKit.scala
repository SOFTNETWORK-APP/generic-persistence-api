package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait RefreshableHeaderSessionTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends HeaderSessionTestKit[T]
    with RefreshableSessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override lazy val headerConfig: HeaderConfig = manager.config.refreshTokenHeaderConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.RefreshableHeader

}
