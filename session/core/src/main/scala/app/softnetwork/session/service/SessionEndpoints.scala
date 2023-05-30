package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import app.softnetwork.session.config.Settings.Session.CookieSecret
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import com.softwaremill.session.{
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
  _: SessionTransportEndpoints[Session] with SessionContinuityEndpoints[Session] =>

  import Session._

  implicit val manager: SessionManager[Session] =
    new SessionManager[Session](SessionConfig.default(CookieSecret))

  implicit def system: ActorSystem[_]

  implicit def ec: ExecutionContext = system.executionContext

  implicit lazy val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    system
  )

}

case class OneOffCookieSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints
    with GenericOneOffCookieSessionEndpoints[Session]

case class OneOffHeaderSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints
    with GenericOneOffHeaderSessionEndpoints[Session]

case class RefreshableCookieSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints
    with GenericRefreshableCookieSessionEndpoints[Session]

case class RefreshableHeaderSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints
    with GenericRefreshableHeaderSessionEndpoints[Session]

object SessionEndpoints {
  def oneOffCookie(
    system: ActorSystem[_],
    checkHeaderAndForm: Boolean = false
  ): SessionEndpoints = OneOffCookieSessionEndpoints(system, checkHeaderAndForm)

  def oneOffHeader(
    system: ActorSystem[_],
    checkHeaderAndForm: Boolean = false
  ): SessionEndpoints = OneOffHeaderSessionEndpoints(system, checkHeaderAndForm)

  def refreshableCookie(
    system: ActorSystem[_],
    checkHeaderAndForm: Boolean = false
  ): SessionEndpoints = RefreshableCookieSessionEndpoints(system, checkHeaderAndForm)

  def refreshableHeader(
    system: ActorSystem[_],
    checkHeaderAndForm: Boolean = false
  ): SessionEndpoints = RefreshableHeaderSessionEndpoints(system, checkHeaderAndForm)
}
