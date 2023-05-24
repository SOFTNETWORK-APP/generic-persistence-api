package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

trait ApiEndpoints extends ApiRoutes {

  import ApiEndpoint._

  def endPoints: ActorSystem[_] => List[ServerEndpoint[Any, Future]]

  override def apiRoutes(system: ActorSystem[_]): Route = {
    implicit def ec: ExecutionContext = system.executionContext
    val routes = endPoints(system)
    routes ++ endpointsToSwaggerEndpoints(routes)
  }

}
