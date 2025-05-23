package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoute, ApiRoutes}
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait SessionServiceRoutes[T <: SessionData with SessionDataDecorator[T]] extends ApiRoutes {
  self: SessionTestKit[T] with SessionMaterials[T] =>
  final def sessionServiceRoute: ActorSystem[_] => SessionServiceRoute[T] = sys =>
    new SessionServiceRoute[T] with SessionMaterials[T] {
      override implicit def manager(implicit
        sessionConfig: SessionConfig,
        companion: SessionDataCompanion[T]
      ): SessionManager[T] = self.manager
      override implicit def ts: ActorSystem[_] = sys
      override protected def sessionType: Session.SessionType = self.sessionType
      override implicit lazy val sessionConfig: SessionConfig = self.sessionConfig

      override implicit def companion: SessionDataCompanion[T] = self.companion

      override implicit def refreshTokenStorage: RefreshTokenStorage[T] = self.refreshTokenStorage
    }

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] =
    system =>
      List(
        sessionServiceRoute(system)
      )
}

trait OneOffCookieSessionServiceTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends OneOffCookieSessionTestKit[T]
    with SessionServiceRoutes[T] { _: Suite with SessionMaterials[T] => }

trait OneOffHeaderSessionServiceTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends OneOffHeaderSessionTestKit[T]
    with SessionServiceRoutes[T] { _: Suite with SessionMaterials[T] => }

trait RefreshableCookieSessionServiceTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends RefreshableCookieSessionTestKit[T]
    with SessionServiceRoutes[T] { _: Suite with SessionMaterials[T] => }

trait RefreshableHeaderSessionServiceTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends RefreshableHeaderSessionTestKit[T]
    with SessionServiceRoutes[T] { _: Suite with SessionMaterials[T] => }
