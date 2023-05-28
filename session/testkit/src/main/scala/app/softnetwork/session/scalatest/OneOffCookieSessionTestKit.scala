package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.session.service.SessionEndpoints
import com.softwaremill.session.{CookieConfig, CsrfCheck}
import org.scalatest.Suite

trait OneOffCookieSessionTestKit extends CookieSessionTestKit with OneOffSessionTestKit {
  _: Suite with CsrfCheck =>

  override def cookieConfig: CookieConfig = sessionManager.config.sessionCookieConfig

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.oneOffCookie(system, checkHeaderAndForm)
}
