package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.session.service.SessionEndpoints
import com.softwaremill.session.{CsrfCheck, HeaderConfig}
import org.scalatest.Suite

trait OneOffHeaderSessionTestKit extends HeaderSessionTestKit with OneOffSessionTestKit {
  _: Suite with CsrfCheck =>

  override def headerConfig: HeaderConfig = sessionManager.config.sessionHeaderConfig

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.oneOffHeader(system, checkHeaderAndForm)

}
