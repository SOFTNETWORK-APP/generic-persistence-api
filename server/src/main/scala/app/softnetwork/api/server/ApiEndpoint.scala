package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiErrors._
import app.softnetwork.api.server.config.ServerSettings
import app.softnetwork.serialization.commonFormats
import org.json4s.Formats
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
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

  import app.softnetwork.serialization._

  protected lazy val errors: EndpointOutput.OneOf[ErrorInfo, ErrorInfo] =
    oneOf[ErrorInfo](
      // returns required http code for different types of ErrorInfo.
      // For secured endpoint you need to define
      // all cases before defining security logic
      oneOfVariant(
        statusCode(StatusCode.Forbidden).and(
          jsonBody[Forbidden]
            .description("When user doesn't have role for the endpoint")
        )
      ),
      oneOfVariant(
        statusCode(StatusCode.Unauthorized).and(
          jsonBody[Unauthorized]
            .description("When user doesn't authenticated or token is expired")
        )
      ),
      oneOfVariant(
        statusCode(StatusCode.NotFound)
          .and(jsonBody[NotFound].description("When something not found"))
      ),
      oneOfVariant(
        statusCode(StatusCode.BadRequest)
          .and(jsonBody[BadRequest].description("Bad request"))
      ),
      oneOfVariant(
        statusCode(StatusCode.InternalServerError)
          .and(jsonBody[InternalServerError].description("For exceptional cases"))
      ),
      // default case below.
      oneOfDefaultVariant(
        jsonBody[ErrorMessage]
          .description("Default result")
          .example(ErrorMessage("Test error message"))
      )
    )

}

object ApiEndpoint {

  val applicationName: String = ServerSettings.ApplicationName

  val applicationVersion: String = ServerSettings.ApplicationVersion

  val swaggerUIOptions: SwaggerUIOptions =
    SwaggerUIOptions
      .default
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
