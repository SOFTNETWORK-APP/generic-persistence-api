package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiEndpoint, ApiEndpoints}
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.SessionEndpoints
import org.scalatest.Suite

trait SessionEndpointsRoutes extends ApiEndpoints { _: CsrfCheck =>
  def sessionEndpoints: ActorSystem[_] => SessionEndpoints

  def sessionServiceEndpoints: ActorSystem[_] => SessionEndpointsRoute = system =>
    SessionEndpointsRoute(system, sessionEndpoints(system))

  override def endpoints: ActorSystem[_] => List[ApiEndpoint] = system =>
    List(sessionServiceEndpoints(system))

}

trait OneOffCookieSessionEndpointsTestKit
    extends OneOffCookieSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.oneOffCookie(system, checkHeaderAndForm)

}

trait OneOffHeaderSessionEndpointsTestKit
    extends OneOffHeaderSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.oneOffHeader(system, checkHeaderAndForm)

}

trait RefreshableCookieSessionEndpointsTestKit
    extends RefreshableCookieSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.refreshableCookie(system, checkHeaderAndForm)

}

trait RefreshableHeaderSessionEndpointsTestKit
    extends RefreshableHeaderSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints.refreshableHeader(system, checkHeaderAndForm)

}
