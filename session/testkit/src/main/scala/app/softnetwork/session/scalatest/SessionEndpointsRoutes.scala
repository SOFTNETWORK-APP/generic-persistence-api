package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiEndpoints, Endpoint}
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.scalatest.Suite
import org.softnetwork.session.model.Session

trait SessionEndpointsRoutes[T <: SessionData with SessionDataDecorator[T]] extends ApiEndpoints {
  self: SessionTestKit[T] with SessionMaterials[T] =>
  def sessionServiceEndpoints: ActorSystem[_] => SessionEndpointsRoute[T] = sys =>
    new SessionEndpointsRoute[T] with SessionMaterials[T] {
      override implicit def sessionConfig: SessionConfig = self.sessionConfig
      override implicit def manager(implicit
        sessionConfig: SessionConfig,
        companion: SessionDataCompanion[T]
      ): SessionManager[T] = self.manager
      override implicit def ts: ActorSystem[_] = sys
      override protected def sessionType: Session.SessionType = self.sessionType
      override implicit def companion: SessionDataCompanion[T] = self.companion
      override implicit def refreshTokenStorage: RefreshTokenStorage[T] = self.refreshTokenStorage
    }

  override def endpoints: ActorSystem[_] => List[Endpoint] = system =>
    List(sessionServiceEndpoints(system))

}

trait OneOffCookieSessionEndpointsTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends OneOffCookieSessionTestKit[T]
    with SessionEndpointsRoutes[T] { _: Suite with CsrfCheck with SessionMaterials[T] => }

trait OneOffHeaderSessionEndpointsTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends OneOffHeaderSessionTestKit[T]
    with SessionEndpointsRoutes[T] { _: Suite with CsrfCheck with SessionMaterials[T] => }

trait RefreshableCookieSessionEndpointsTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends RefreshableCookieSessionTestKit[T]
    with SessionEndpointsRoutes[T] { _: Suite with CsrfCheck with SessionMaterials[T] => }

trait RefreshableHeaderSessionEndpointsTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends RefreshableHeaderSessionTestKit[T]
    with SessionEndpointsRoutes[T] { _: Suite with CsrfCheck with SessionMaterials[T] => }
