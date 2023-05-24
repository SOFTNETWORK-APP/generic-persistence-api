package app.softnetwork.persistence.person

import app.softnetwork.persistence.message.{
  Command,
  CommandResult,
  Created,
  CrudEvent,
  Deleted,
  ErrorMessage,
  Updated,
  Upserted,
  UpsertedDecorator
}
import app.softnetwork.persistence.person.model.Person

import java.time.Instant

package object message {
  sealed trait PersonCommand extends Command
  case class AddPerson(name: String, birthDate: String) extends PersonCommand
  case class UpdatePerson(name: String, birthDate: String) extends PersonCommand
  case class UpdateName(name: String) extends PersonCommand
  case object LoadPerson extends PersonCommand
  case object DeletePerson extends PersonCommand

  sealed trait PersonCommandResult extends CommandResult
  case class PersonAdded(uuid: String) extends PersonCommandResult
  case object PersonUpdated extends PersonCommandResult
  case object NameUpdated extends PersonCommandResult
  case object PersonDeleted extends PersonCommandResult
  case class PersonLoaded(person: Person) extends PersonCommandResult
  abstract class PersonError(error: String) extends ErrorMessage(error) with PersonCommandResult
  case object PersonNotFound extends PersonError("PersonNotFound")

  sealed trait PersonEvent extends CrudEvent
  case class PersonCreatedEvent(document: Person) extends Created[Person] with PersonEvent
  case class PersonUpdatedEvent(document: Person) extends Updated[Person] with PersonEvent
  case class NameUpdatedEvent(uuid: String, name: String, lastUpdated: Instant)
      extends Upserted
      with UpsertedDecorator
      with PersonEvent
  case class PersonDeletedEvent(uuid: String) extends Deleted with PersonEvent
}
