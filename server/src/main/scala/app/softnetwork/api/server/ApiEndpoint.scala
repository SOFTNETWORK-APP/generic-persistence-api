package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.serialization.commonFormats
import org.json4s.Formats
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ApiEndpoint extends SwaggerApiEndpoint {

  import ApiEndpoint._

  def endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]

  def swaggerEndpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]] =
    endpointsToSwaggerEndpoints(endpoints)

  def swaggerRoute(implicit ec: ExecutionContext): Route = swaggerEndpoints

  def apiRoute(implicit ec: ExecutionContext): Route = endpoints

  implicit def formats: Formats = commonFormats

}

object ApiEndpoint {

  implicit def endpointsToRoute(
    endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]
  )(implicit
    ec: ExecutionContext
  ): Route = AkkaHttpServerInterpreter().toRoute(endpoints)

}
