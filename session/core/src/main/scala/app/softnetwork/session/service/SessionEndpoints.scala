package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import app.softnetwork.session.config.Settings.Session.CookieSecret
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import com.softwaremill.session.{
  CsrfCheck,
  GenericOneOffCookieSessionEndpoints,
  GenericOneOffHeaderSessionEndpoints,
  GenericRefreshableCookieSessionEndpoints,
  GenericRefreshableHeaderSessionEndpoints,
  GenericSessionEndpoints,
  RefreshTokenStorage,
  SessionConfig,
  SessionContinuityEndpoints,
  SessionManager,
  SessionTransportEndpoints
}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait SessionEndpoints extends GenericSessionEndpoints[Session] {
  _: SessionTransportEndpoints[Session] with SessionContinuityEndpoints[Session] with CsrfCheck =>

  import Session._

  implicit val manager: SessionManager[Session] =
    new SessionManager[Session](SessionConfig.default(CookieSecret))

  implicit def system: ActorSystem[_]

  implicit def ec: ExecutionContext = system.executionContext

  implicit lazy val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    system
  )

}

trait OneOffCookieSessionEndpoints
    extends SessionEndpoints
    with GenericOneOffCookieSessionEndpoints[Session] {
  _: CsrfCheck =>
}

trait OneOffHeaderSessionEndpoints
    extends SessionEndpoints
    with GenericOneOffHeaderSessionEndpoints[Session] {
  _: CsrfCheck =>
}

trait RefreshableCookieSessionEndpoints
    extends SessionEndpoints
    with GenericRefreshableCookieSessionEndpoints[Session] {
  _: CsrfCheck =>
}

trait RefreshableHeaderSessionEndpoints
    extends SessionEndpoints
    with GenericRefreshableHeaderSessionEndpoints[Session] {
  _: CsrfCheck =>
}
