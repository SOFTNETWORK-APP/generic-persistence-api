package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import app.softnetwork.session.config.Settings.Session.CookieSecret
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import com.softwaremill.session.{
  CookieTransportEndpoints,
  CsrfCheck,
  GenericSessionEndpoints,
  HeaderTransportEndpoints,
  OneOffSessionContinuity,
  RefreshTokenStorage,
  RefreshableSessionContinuity,
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

trait CookieSessionEndpoints extends SessionEndpoints with CookieTransportEndpoints[Session] {
  _: SessionContinuityEndpoints[Session] with CsrfCheck =>

}

trait HeaderSessionEndpoints extends SessionEndpoints with HeaderTransportEndpoints[Session] {
  _: SessionContinuityEndpoints[Session] with CsrfCheck =>
}

trait OneOffCookieSessionEndpoints
    extends CookieSessionEndpoints
    with OneOffSessionContinuity[Session] {
  _: CsrfCheck =>
}

trait OneOffHeaderSessionEndpoints
    extends HeaderSessionEndpoints
    with OneOffSessionContinuity[Session] {
  _: CsrfCheck =>
}

trait RefreshableCookieSessionEndpoints
    extends CookieSessionEndpoints
    with RefreshableSessionContinuity[Session] { _: CsrfCheck => }

trait RefreshableHeaderSessionEndpoints
    extends HeaderSessionEndpoints
    with RefreshableSessionContinuity[Session] { _: CsrfCheck => }
