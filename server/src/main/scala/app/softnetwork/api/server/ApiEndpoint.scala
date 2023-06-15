package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.config.ServerSettings
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ApiEndpoint {

  import ApiEndpoint._

  val applicationName: String = ServerSettings.ApplicationName

  val applicationVersion: String = ServerSettings.ApplicationVersion

  def endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]

  def swaggerEndpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]] =
    endpointsToSwaggerEndpoints(endpoints, applicationName, applicationVersion)

  def swaggerRoute(implicit ec: ExecutionContext): Route = swaggerEndpoints

  def apiRoute(implicit ec: ExecutionContext): Route = endpoints

}

object ApiEndpoint {

  def endpointsToSwaggerEndpoints(
    endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]],
    applicationName: String,
    applicationVersion: String
  ): List[ServerEndpoint[AkkaStreams with WebSockets, Future]] =
    SwaggerInterpreter().fromEndpoints[Future](
      endpoints.map(_.endpoint.prependIn(config.ServerSettings.RootPath)),
      applicationName,
      applicationVersion
    )

  implicit def endpointsToRoute(
    endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]
  )(implicit
    ec: ExecutionContext
  ): Route = AkkaHttpServerInterpreter().toRoute(endpoints)

}
