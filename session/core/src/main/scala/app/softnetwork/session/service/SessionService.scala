package app.softnetwork.session.service

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
trait SessionService extends SessionDirectives { _: SessionMaterials =>

  import com.softwaremill.session.SessionOptions._

  def sc(implicit manager: SessionManager[Session]): SessionContinuity[Session] =
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

  def checkMode(implicit manager: SessionManager[Session]): CsrfCheckMode[Session] =
    if (headerAndForm) {
      CsrfOptions.checkHeaderAndForm
    } else {
      CsrfOptions.checkHeader
    }

}
