package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoute, ApiRoutes}
import app.softnetwork.session.service.SessionService
import org.scalatest.Suite

trait SessionServiceRoutes extends ApiRoutes {
  def sessionService: ActorSystem[_] => SessionService

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
  override def sessionService: ActorSystem[_] => SessionService = system =>
    SessionService.oneOffCookie(system)
}

trait OneOffHeaderSessionServiceTestKit
    extends OneOffHeaderSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionService: ActorSystem[_] => SessionService = system =>
    SessionService.oneOffHeader(system)
}

trait RefreshableCookieSessionServiceTestKit
    extends RefreshableCookieSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionService: ActorSystem[_] => SessionService = system =>
    SessionService.refreshableCookie(system)
}

trait RefreshableHeaderSessionServiceTestKit
    extends RefreshableHeaderSessionTestKit
    with SessionServiceRoutes { _: Suite =>
  override def sessionService: ActorSystem[_] => SessionService = system =>
    SessionService.refreshableHeader(system)
}
