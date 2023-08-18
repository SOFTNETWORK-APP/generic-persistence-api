package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.config.ServerSettings
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.language.reflectiveCalls

trait SwaggerEndpoint extends Endpoint {

  import Endpoint._

  val applicationName: String = ServerSettings.ApplicationName

  val applicationVersion: String = ServerSettings.ApplicationVersion

  val swaggerPathPrefix: List[String] = config.ServerSettings.SwaggerPathPrefix

  val swaggerUIOptions: SwaggerUIOptions = SwaggerUIOptions.default.pathPrefix(swaggerPathPrefix)

  final def swaggerRoute: Route = endpointsToSwaggerEndpoints(endpoints)

  override def route: Route = swaggerRoute

  def endpointsToSwaggerEndpoints(
    endpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]
  ): List[ServerEndpoint[AkkaStreams with WebSockets, Future]] =
    SwaggerInterpreter(swaggerUIOptions = swaggerUIOptions).fromEndpoints[Future](
      endpoints.map(_.endpoint.prependIn(config.ServerSettings.RootPath)),
      applicationName,
      applicationVersion
    )

}
