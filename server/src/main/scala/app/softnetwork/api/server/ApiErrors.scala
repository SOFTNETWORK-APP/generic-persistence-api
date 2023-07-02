package app.softnetwork.api.server

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import org.json4s.Formats

object ApiErrors {

  sealed trait ErrorInfo {
    val message: String
  }

  /** Represents http 404. */
  case class NotFound(message: String) extends ErrorInfo

  /** Represents http 401. */
  case class Unauthorized(message: String) extends ErrorInfo

  /** Represents http 403. */
  case class Forbidden(message: String) extends ErrorInfo

  /** Represents http 409. */
  case class Conflict(message: String) extends ErrorInfo

  /** Default case. */
  case class ErrorMessage(message: String) extends ErrorInfo

  /** Represents http 400. */
  case class BadRequest(message: String) extends ErrorInfo

  /** Represents http 500. */
  case class InternalServerError(message: String) extends ErrorInfo

  implicit def apiError2Route(apiError: ErrorInfo)(implicit formats: Formats): Route =
    apiError match {
      case r: BadRequest => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
      case r: Conflict   => complete(HttpResponse(StatusCodes.Conflict, entity = r))
      case r: Forbidden  => complete(HttpResponse(StatusCodes.Forbidden, entity = r))
      case r: InternalServerError =>
        complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
      case r: NotFound     => complete(HttpResponse(StatusCodes.NotFound, entity = r))
      case r: Unauthorized => complete(HttpResponse(StatusCodes.Unauthorized, entity = r))
      case r: ErrorMessage => complete(HttpResponse(StatusCodes.OK, entity = r))
    }

}
