package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffHeaderSessionTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends HeaderSessionTestKit[T]
    with OneOffSessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override def headerConfig: HeaderConfig = manager.config.sessionHeaderConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.OneOffHeader

}
