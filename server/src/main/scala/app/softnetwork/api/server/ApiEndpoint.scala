package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.persistence.{appName, version}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.{ExecutionContext, Future}

import scala.language.implicitConversions

trait ApiEndpoint {

  import ApiEndpoint._

  def endpoints: List[ServerEndpoint[Any, Future]]

  def swaggerEndpoints: List[ServerEndpoint[Any, Future]] = endpointsToSwaggerEndpoints(endpoints)

  def swaggerRoute(implicit ec: ExecutionContext): Route = swaggerEndpoints

  def apiRoute(implicit ec: ExecutionContext): Route = endpoints

}

object ApiEndpoint {

  def endpointsToSwaggerEndpoints(
    endpoints: List[ServerEndpoint[Any, Future]]
  ): List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter().fromEndpoints[Future](
      endpoints.map(_.endpoint.prependIn(config.ServerSettings.RootPath)),
      appName,
      version
    )

  implicit def endpointsToRoute(endpoints: List[ServerEndpoint[Any, Future]])(implicit
    ec: ExecutionContext
  ): Route = AkkaHttpServerInterpreter().toRoute(endpoints)

}
