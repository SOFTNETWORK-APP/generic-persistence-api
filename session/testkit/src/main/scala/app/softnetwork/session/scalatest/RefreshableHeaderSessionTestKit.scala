package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.SessionData
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait RefreshableHeaderSessionTestKit[T <: SessionData]
    extends HeaderSessionTestKit[T]
    with RefreshableSessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override def headerConfig: HeaderConfig = manager.config.refreshTokenHeaderConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.RefreshableHeader

}
