package app.softnetwork.api.server

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
}
