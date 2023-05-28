package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.session.service.SessionEndpoints
import com.softwaremill.session.{CsrfCheck, HeaderConfig}
import org.scalatest.Suite

trait RefreshableHeaderSessionTestKit extends HeaderSessionTestKit with RefreshableSessionTestKit {
  _: Suite with CsrfCheck =>

  override def headerConfig: HeaderConfig = sessionManager.config.refreshTokenHeaderConfig

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.refreshableHeader(system)

}
