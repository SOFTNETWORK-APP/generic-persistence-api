package app.softnetwork.session.service

import app.softnetwork.session.model.SessionData
import app.softnetwork.session.{
  TapirCsrfCheckMode,
  TapirCsrfOptions,
  TapirEndpoints,
  TapirSessionContinuity
}
import com.softwaremill.session._
import org.softnetwork.session.model.Session

import scala.language.implicitConversions

trait SessionEndpoints[T <: SessionData] extends TapirEndpoints { _: SessionMaterials[T] =>

  import app.softnetwork.session.TapirSessionOptions._

  def sc(implicit manager: SessionManager[T]): TapirSessionContinuity[T] =
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

  def checkMode(implicit manager: SessionManager[T]): TapirCsrfCheckMode[T] =
    if (headerAndForm) {
      TapirCsrfOptions.checkHeaderAndForm
    } else {
      TapirCsrfOptions.checkHeader
    }

}
