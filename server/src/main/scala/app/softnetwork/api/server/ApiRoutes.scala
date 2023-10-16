package app.softnetwork.api.server

import java.util.concurrent.TimeoutException
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{
  AuthorizationFailedRejection,
  Directives,
  ExceptionHandler,
  MethodRejection,
  MissingCookieRejection,
  RejectionHandler,
  Route,
  ValidationRejection
}
import app.softnetwork.api.server.config.ServerSettings
import org.json4s.Formats
import app.softnetwork.serialization._
import org.slf4j.Logger

import scala.util.{Failure, Success, Try}

/** Created by smanciot on 19/04/2021.
  */
trait ApiRoutes extends Directives with GrpcServices with DefaultComplete {

  val applicationVersion: String = ServerSettings.ApplicationVersion

  override implicit def formats: Formats = commonFormats

  def log: Logger

  val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case MissingCookieRejection(cookieName) =>
        complete(HttpResponse(StatusCodes.BadRequest, entity = s"$cookieName cookie required"))
      }
      .handle { case AuthorizationFailedRejection =>
        complete(StatusCodes.Forbidden)
      }
      .handle { case ValidationRejection(msg, _) =>
        complete(HttpResponse(StatusCodes.InternalServerError, entity = msg))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(
          HttpResponse(
            StatusCodes.MethodNotAllowed,
            entity = s"Supported methods: ${names mkString " or "}!"
          )
        )
      }
      .handleNotFound {
        complete(HttpResponse(StatusCodes.NotFound, entity = "Not found"))
      }
      .result()

  val timeoutExceptionHandler: ExceptionHandler =
    ExceptionHandler { case e: TimeoutException =>
      extractUri { uri =>
        log.error(
          s"Request to $uri could not be handled normally -> ${e.getMessage}",
          e.getCause
        )
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "Timeout"))
      }
    }

  final def mainRoutes: ActorSystem[_] => Route = system => {
    val routes = concat((HealthCheckService :: apiRoutes(system)).map(_.route): _*)
    handleRejections(rejectionHandler) {
      handleExceptions(timeoutExceptionHandler) {
        logRequestResult("RestAll") {
          pathPrefix(config.ServerSettings.RootPath) {
            Try(
              respondWithHeaders(RawHeader("Api-Version", applicationVersion)) {
                routes
              }
            ) match {
              case Success(s) => s
              case Failure(f) =>
                log.error(f.getMessage, f.getCause)
                complete(
                  HttpResponse(
                    StatusCodes.InternalServerError,
                    entity = f.getMessage
                  )
                )
            }
          }
        }
      }
    } ~ grpcRoutes(system)
  }

  def apiRoutes: ActorSystem[_] => List[ApiRoute]

}
