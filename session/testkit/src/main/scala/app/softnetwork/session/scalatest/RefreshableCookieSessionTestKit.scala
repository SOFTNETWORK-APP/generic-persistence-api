package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.session.service.SessionEndpoints
import com.softwaremill.session.{CookieConfig, CsrfCheck}
import org.scalatest.Suite

trait RefreshableCookieSessionTestKit extends CookieSessionTestKit with RefreshableSessionTestKit {
  _: Suite with CsrfCheck =>

  override def cookieConfig: CookieConfig = sessionManager.config.refreshTokenCookieConfig

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.refreshableCookie(system, checkHeaderAndForm)

}
