package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.{ExecutionContext, Future}

trait Endpoint extends ApiRoute {

  implicit def ec: ExecutionContext

  def endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]

}

object Endpoint {

  implicit def endpointsToRoute(
    endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]
  )(implicit
    ec: ExecutionContext
  ): Route = AkkaHttpServerInterpreter().toRoute(endpoints)

}
