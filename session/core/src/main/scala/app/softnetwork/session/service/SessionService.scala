package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.{Directive0, Directive1}
import com.softwaremill.session.{
  RefreshTokenStorage,
  SessionContinuity,
  SessionDirectives,
  SetSessionTransport
}
import com.softwaremill.session.SessionOptions._
import org.softnetwork.session.model.Session
import app.softnetwork.session.handlers.SessionRefreshTokenDao

import scala.concurrent.ExecutionContext

/** Created by smanciot on 05/07/2018.
  */
trait SessionService {

  implicit def system: ActorSystem[_]

  implicit def ec: ExecutionContext = system.executionContext

  implicit lazy val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    system
  )

  def sessionContinuity: SessionContinuity[Session]

  def sessionTransport: SetSessionTransport

  def setSession(session: Session): Directive0 =
    SessionDirectives.setSession(sessionContinuity, sessionTransport, session)

  def requiredSession: Directive1[Session] =
    SessionDirectives.requiredSession(sessionContinuity, sessionTransport)

  def invalidateSession: Directive0 =
    SessionDirectives.invalidateSession(sessionContinuity, sessionTransport)

  def optionalSession: Directive1[Option[Session]] =
    SessionDirectives.optionalSession(sessionContinuity, sessionTransport)
}

case class OneOffCookieSessionService(system: ActorSystem[_]) extends SessionService {
  import Session._

  override def sessionContinuity: SessionContinuity[Session] = oneOff

  override def sessionTransport: SetSessionTransport = usingCookies
}

case class OneOffHeaderSessionService(system: ActorSystem[_]) extends SessionService {
  import Session._

  override def sessionContinuity: SessionContinuity[Session] = oneOff

  override def sessionTransport: SetSessionTransport = usingHeaders
}

case class RefreshableCookieSessionService(system: ActorSystem[_]) extends SessionService {
  import Session._

  override def sessionContinuity: SessionContinuity[Session] = refreshable

  override def sessionTransport: SetSessionTransport = usingCookies
}

case class RefreshableHeaderSessionService(system: ActorSystem[_]) extends SessionService {
  import Session._

  override def sessionContinuity: SessionContinuity[Session] = refreshable

  override def sessionTransport: SetSessionTransport = usingHeaders
}

object SessionService {
  def oneOffCookie(system: ActorSystem[_]): SessionService = OneOffCookieSessionService(system)

  def oneOffHeader(system: ActorSystem[_]): SessionService = OneOffHeaderSessionService(system)

  def refreshableCookie(system: ActorSystem[_]): SessionService = RefreshableCookieSessionService(
    system
  )

  def refreshableHeader(system: ActorSystem[_]): SessionService = RefreshableHeaderSessionService(
    system
  )
}
