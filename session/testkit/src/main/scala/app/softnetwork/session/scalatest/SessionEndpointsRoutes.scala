package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiEndpoints, Endpoint}
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait SessionEndpointsRoutes extends ApiEndpoints { self: SessionTestKit with SessionMaterials =>
  def sessionServiceEndpoints: ActorSystem[_] => SessionEndpointsRoute = sys =>
    new SessionEndpointsRoute with SessionMaterials {
      override implicit def sessionConfig: SessionConfig = self.sessionConfig
      override implicit def manager(implicit
        sessionConfig: SessionConfig
      ): SessionManager[Session] = self.manager
      override implicit def ts: ActorSystem[_] = sys
      override protected def sessionType: Session.SessionType = self.sessionType
    }

  override def endpoints: ActorSystem[_] => List[Endpoint] = system =>
    List(sessionServiceEndpoints(system))

}

trait OneOffCookieSessionEndpointsTestKit
    extends OneOffCookieSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck with SessionMaterials => }

trait OneOffHeaderSessionEndpointsTestKit
    extends OneOffHeaderSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck with SessionMaterials => }

trait RefreshableCookieSessionEndpointsTestKit
    extends RefreshableCookieSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck with SessionMaterials => }

trait RefreshableHeaderSessionEndpointsTestKit
    extends RefreshableHeaderSessionTestKit
    with SessionEndpointsRoutes { _: Suite with CsrfCheck with SessionMaterials => }
