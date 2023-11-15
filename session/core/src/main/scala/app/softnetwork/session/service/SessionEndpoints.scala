package app.softnetwork.session.service

import app.softnetwork.session.{
  TapirCsrfCheckMode,
  TapirCsrfOptions,
  TapirEndpoints,
  TapirSessionContinuity
}
import com.softwaremill.session._
import org.softnetwork.session.model.Session

import scala.language.implicitConversions

trait SessionEndpoints extends TapirEndpoints { _: SessionMaterials =>

  import app.softnetwork.session.TapirSessionOptions._

  def sc(implicit manager: SessionManager[Session]): TapirSessionContinuity[Session] =
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

  def checkMode(implicit manager: SessionManager[Session]): TapirCsrfCheckMode[Session] =
    if (headerAndForm) {
      TapirCsrfOptions.checkHeaderAndForm
    } else {
      TapirCsrfOptions.checkHeader
    }

}
