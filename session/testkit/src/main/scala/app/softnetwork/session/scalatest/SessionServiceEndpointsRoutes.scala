package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.ApiEndpoints
import app.softnetwork.session.service.SessionEndpoints
import com.softwaremill.session.CsrfCheck
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

trait SessionServiceEndpointsRoutes extends SessionServiceRoutes with ApiEndpoints { _: CsrfCheck =>
  def sessionEndpoints: ActorSystem[_] => SessionEndpoints

  def sessionServiceEndpoints: ActorSystem[_] => SessionServiceEndpoints = system =>
    SessionServiceEndpoints(system, sessionEndpoints(system))

  override def endpoints
    : ActorSystem[_] => List[ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]] =
    system => sessionServiceEndpoints(system).endpoints

}
