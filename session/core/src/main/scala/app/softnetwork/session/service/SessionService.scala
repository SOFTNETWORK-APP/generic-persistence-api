package app.softnetwork.session.service

import app.softnetwork.session.model.{SessionData, SessionDataDecorator}
import com.softwaremill.session.{
  CsrfCheckMode,
  CsrfOptions,
  GetSessionTransport,
  SessionContinuity,
  SessionDirectives,
  SessionManager,
  SetSessionTransport
}
import org.softnetwork.session.model.Session

/** Created by smanciot on 05/07/2018.
  */
trait SessionService[T <: SessionData with SessionDataDecorator[T]] extends SessionDirectives {
  _: SessionMaterials[T] =>

  import com.softwaremill.session.SessionOptions._

  def sc(implicit manager: SessionManager[T]): SessionContinuity[T] =
    sessionType match {
      case Session.SessionType.OneOffCookie | Session.SessionType.OneOffHeader => oneOff
      case _                                                                   => refreshable
    }

  lazy val st: SetSessionTransport =
    sessionType match {
      case Session.SessionType.OneOffCookie | Session.SessionType.RefreshableCookie => usingCookies
      case _                                                                        => usingHeaders
    }

  lazy val gt: GetSessionTransport = st

  def checkMode(implicit manager: SessionManager[T]): CsrfCheckMode[T] =
    if (headerAndForm) {
      CsrfOptions.checkHeaderAndForm
    } else {
      CsrfOptions.checkHeader
    }

}
