package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.config.ServerSettings
import app.softnetwork.serialization.commonFormats
import org.json4s.Formats
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir._
import sttp.tapir.swagger.SwaggerUIOptions

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ApiEndpoint {

  import ApiEndpoint._

  def endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]

  def swaggerEndpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]] =
    endpointsToSwaggerEndpoints(endpoints, swaggerUIOptions)

  def swaggerRoute(implicit ec: ExecutionContext): Route = swaggerEndpoints

  def apiRoute(implicit ec: ExecutionContext): Route = endpoints

  implicit def formats: Formats = commonFormats

}

object ApiEndpoint {

  val applicationName: String = ServerSettings.ApplicationName

  val applicationVersion: String = ServerSettings.ApplicationVersion

  val swaggerUIOptions: SwaggerUIOptions =
    SwaggerUIOptions.default
      .pathPrefix(config.ServerSettings.SwaggerPathPrefix)

  def endpointsToSwaggerEndpoints(
    endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]],
    swaggerUIOptions: SwaggerUIOptions
  ): List[ServerEndpoint[AkkaStreams with WebSockets, Future]] =
    SwaggerInterpreter(swaggerUIOptions = swaggerUIOptions).fromEndpoints[Future](
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
