package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait OneOffHeaderSessionTestKit extends HeaderSessionTestKit with OneOffSessionTestKit {
  _: Suite with ApiRoutes with SessionMaterials =>

  override def headerConfig: HeaderConfig = manager.config.sessionHeaderConfig

  override protected val sessionType: Session.SessionType = Session.SessionType.OneOffHeader

}
