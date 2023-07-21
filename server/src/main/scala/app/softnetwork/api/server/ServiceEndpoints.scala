package app.softnetwork.api.server

import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.message.{Command, CommandResult, ErrorMessage}
import app.softnetwork.persistence.service.Service
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import sttp.tapir.generic.auto.SchemaDerivation
import sttp.tapir.Tapir
import sttp.tapir.json.json4s.TapirJson4s

import scala.language.implicitConversions

trait ServiceEndpoints[C <: Command, R <: CommandResult]
    extends ApiEndpoint
    with Tapir
    with SchemaDerivation
    with TapirJson4s
    with Service[C, R]
    with Completion { _: EntityPattern[C, R] =>

  implicit def resultToApiError(result: R): ApiErrors.ErrorInfo =
    result match {
      case error: ErrorMessage => ApiErrors.ErrorMessage(error.message)
      case _                   => ApiErrors.ErrorMessage("Unknown")
    }
}
