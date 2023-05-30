package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite

trait OneOffHeaderSessionTestKit extends HeaderSessionTestKit with OneOffSessionTestKit {
  _: Suite with ApiRoutes =>

  override def headerConfig: HeaderConfig = sessionManager.config.sessionHeaderConfig

}
