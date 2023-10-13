package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiEndpoint, ApiEndpoints}
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.launch.SessionServiceAndEndpoints
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait SessionEndpointsRoutes extends ApiEndpoints with SessionServiceAndEndpoints { _: CsrfCheck =>
  def sessionServiceEndpoints: ActorSystem[_] => SessionEndpointsRoute = system =>
    SessionEndpointsRoute(system, sessionEndpoints(system))

  override def endpoints: ActorSystem[_] => List[ApiEndpoint] = system =>
    List(sessionServiceEndpoints(system))

}

trait OneOffCookieSessionEndpointsTestKit
    extends OneOffCookieSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionType: Session.SessionType = Session.SessionType.OneOffCookie

}

trait OneOffHeaderSessionEndpointsTestKit
    extends OneOffHeaderSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionType: Session.SessionType = Session.SessionType.OneOffHeader

}

trait RefreshableCookieSessionEndpointsTestKit
    extends RefreshableCookieSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionType: Session.SessionType = Session.SessionType.RefreshableCookie

}

trait RefreshableHeaderSessionEndpointsTestKit
    extends RefreshableHeaderSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck =>

  override def sessionType: Session.SessionType = Session.SessionType.RefreshableHeader

}
