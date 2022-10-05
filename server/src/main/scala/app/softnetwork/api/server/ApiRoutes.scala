package app.softnetwork.api.server

import java.util.concurrent.TimeoutException
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import app.softnetwork.persistence.version
import com.typesafe.scalalogging.StrictLogging
import org.json4s.Formats
import app.softnetwork.serialization._

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 19/04/2021.
  */
trait ApiRoutes extends Directives with GrpcServices with DefaultComplete with StrictLogging {

  override implicit def formats: Formats = commonFormats

  val timeoutExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: TimeoutException =>
        extractUri { uri =>
          logger.error(s"Request to $uri could not be handled normally -> ${e.getMessage}", e.getCause)
          complete(HttpResponse(StatusCodes.InternalServerError, entity = "Timeout"))
        }
    }

  final def mainRoutes: ActorSystem[_] => Route = system => {
    handleExceptions(timeoutExceptionHandler) {
      logRequestResult("RestAll") {
        pathPrefix(config.Settings.RootPath) {
          Try(
            respondWithHeaders(RawHeader("Api-Version", version)){
              HealthCheckService.route ~ apiRoutes(system)
            }
          ) match {
            case Success(s) => s
            case Failure(f) =>
              logger.error(f.getMessage, f.getCause)
              complete(
                HttpResponse(
                  StatusCodes.InternalServerError,
                  entity = f.getMessage
                )
              )
          }
        }
      }
    } ~ grpcRoutes(system)
  }

  def apiRoutes(system: ActorSystem[_]): Route
}
