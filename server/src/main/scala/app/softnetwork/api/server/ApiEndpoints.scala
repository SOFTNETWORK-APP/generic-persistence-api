package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.config.ServerSettings
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

trait ApiEndpoints extends ApiRoutes {

  import ApiEndpoint._

  val applicationName: String = ServerSettings.ApplicationName

  def endpoints: ActorSystem[_] => List[ServerEndpoint[AkkaStreams with WebSockets, Future]]

  override def apiRoutes(system: ActorSystem[_]): Route = {
    implicit def ec: ExecutionContext = system.executionContext
    val eps = endpoints(system)
    eps ++ endpointsToSwaggerEndpoints(eps, applicationName, applicationVersion)
  }

}
