package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoute, ApiRoutes}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{SessionConfig, SessionManager}
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait SessionServiceRoutes extends ApiRoutes { self: SessionTestKit with SessionMaterials =>
  final def sessionServiceRoute: ActorSystem[_] => SessionServiceRoute = sys =>
    new SessionServiceRoute with SessionMaterials {
      override implicit def manager(implicit
        sessionConfig: SessionConfig
      ): SessionManager[Session] = self.manager
      override implicit def ts: ActorSystem[_] = sys
      override protected def sessionType: Session.SessionType = self.sessionType
      override implicit def sessionConfig: SessionConfig = self.sessionConfig
    }

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] =
    system =>
      List(
        sessionServiceRoute(system)
      )
}

trait OneOffCookieSessionServiceTestKit
    extends OneOffCookieSessionTestKit
    with SessionServiceRoutes { _: Suite with SessionMaterials => }

trait OneOffHeaderSessionServiceTestKit
    extends OneOffHeaderSessionTestKit
    with SessionServiceRoutes { _: Suite with SessionMaterials => }

trait RefreshableCookieSessionServiceTestKit
    extends RefreshableCookieSessionTestKit
    with SessionServiceRoutes { _: Suite with SessionMaterials => }

trait RefreshableHeaderSessionServiceTestKit
    extends RefreshableHeaderSessionTestKit
    with SessionServiceRoutes { _: Suite with SessionMaterials => }
