package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import app.softnetwork.session.{TapirCsrfCheckMode, TapirEndpoints, TapirSessionContinuity}
import app.softnetwork.session.config.Settings.Session.CookieSecret
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import com.softwaremill.session._
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait SessionEndpoints extends TapirEndpoints {

  implicit val manager: SessionManager[Session] =
    new SessionManager[Session](SessionConfig.default(CookieSecret))

  implicit def system: ActorSystem[_]

  implicit def ec: ExecutionContext = system.executionContext

  implicit lazy val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    system
  )

  import app.softnetwork.session.TapirSessionOptions._

  lazy val oneOffSession: TapirSessionContinuity[Session] = oneOff

  lazy val refreshableSession: TapirSessionContinuity[Session] = refreshable

  import app.softnetwork.session.TapirCsrfOptions._

  lazy val checkHeaderMode: TapirCsrfCheckMode[Session] = checkHeader

  lazy val checkHeaderAndFormMode: TapirCsrfCheckMode[Session] = checkHeaderAndForm

  lazy val sc: TapirSessionContinuity[Session] = oneOffSession

  lazy val st: SetSessionTransport = CookieST

  lazy val gt: GetSessionTransport = st

  lazy val checkMode: TapirCsrfCheckMode[Session] = checkHeaderMode

  implicit def booleanToCheckMode(checkHeaderAndForm: Boolean): TapirCsrfCheckMode[Session] =
    if (checkHeaderAndForm) {
      checkHeaderAndFormMode
    } else {
      checkHeaderMode
    }
}

case class OneOffCookieSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints {
  override lazy val sc: TapirSessionContinuity[Session] = oneOffSession
  override lazy val st: SetSessionTransport = CookieST
  override lazy val checkMode: TapirCsrfCheckMode[Session] = checkHeaderAndForm
}

case class OneOffHeaderSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints {
  override lazy val sc: TapirSessionContinuity[Session] = oneOffSession
  override lazy val st: SetSessionTransport = HeaderST
  override lazy val checkMode: TapirCsrfCheckMode[Session] = checkHeaderAndForm
}

case class RefreshableCookieSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints {
  override lazy val sc: TapirSessionContinuity[Session] = refreshableSession
  override lazy val st: SetSessionTransport = CookieST
  override lazy val checkMode: TapirCsrfCheckMode[Session] = checkHeaderAndForm
}

case class RefreshableHeaderSessionEndpoints(system: ActorSystem[_], checkHeaderAndForm: Boolean)
    extends SessionEndpoints {
  override lazy val sc: TapirSessionContinuity[Session] = refreshableSession
  override lazy val st: SetSessionTransport = HeaderST
  override lazy val checkMode: TapirCsrfCheckMode[Session] = checkHeaderAndForm
}

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
