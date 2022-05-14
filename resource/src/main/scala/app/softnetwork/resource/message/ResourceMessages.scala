package app.softnetwork.resource.message

import app.softnetwork.resource.model.Resource
import app.softnetwork.persistence.message._

/**
  * Created by smanciot on 07/07/2018.
  */
object ResourceMessages {
  sealed trait ResourceCommand extends Command

  case class CreateResource(uuid: String, bytes: Array[Byte]) extends ResourceCommand

  case class UpdateResource(uuid: String, bytes: Array[Byte]) extends ResourceCommand

  case class LoadResource(uuid: String) extends ResourceCommand

  case class DeleteResource(uuid: String) extends ResourceCommand

  sealed trait ResourceResult extends CommandResult

  case object ResourceCreated extends ResourceResult

  case object ResourceUpdated extends ResourceResult

  case class ResourceLoaded(resource: Resource) extends ResourceResult

  case object ResourceDeleted extends ResourceResult

  class ResourceError(override val message: String) extends ErrorMessage(message) with ResourceResult

  case object ResourceNotFound extends ResourceError("ResourceNotFound")
}
