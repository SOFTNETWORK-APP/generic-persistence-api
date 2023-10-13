package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoute, ApiRoutes}
import app.softnetwork.session.CsrfCheckHeader
import app.softnetwork.session.launch.SessionServiceAndEndpoints
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait SessionServiceRoutes extends ApiRoutes with SessionServiceAndEndpoints with CsrfCheckHeader {
  final def sessionServiceRoute: ActorSystem[_] => SessionServiceRoute = system =>
    SessionServiceRoute(sessionService(system))

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] =
    system =>
      List(
        sessionServiceRoute(system)
      )
}

trait OneOffCookieSessionServiceTestKit
    extends OneOffCookieSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionType: Session.SessionType = Session.SessionType.OneOffCookie
}

trait OneOffHeaderSessionServiceTestKit
    extends OneOffHeaderSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionType: Session.SessionType = Session.SessionType.OneOffHeader
}

trait RefreshableCookieSessionServiceTestKit
    extends RefreshableCookieSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionType: Session.SessionType = Session.SessionType.RefreshableCookie
}

trait RefreshableHeaderSessionServiceTestKit
    extends RefreshableHeaderSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionType: Session.SessionType = Session.SessionType.RefreshableHeader
}
