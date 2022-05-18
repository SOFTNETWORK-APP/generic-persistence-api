package app.softnetwork.kv

import app.softnetwork.persistence.message.{Command, CommandResult, ErrorMessage, Event}

package object message {

  sealed trait KeyValueCommand extends Command

  @SerialVersionUID(0L)
  case class AddKeyValue(value: String) extends KeyValueCommand

  case object RemoveKeyValue extends KeyValueCommand

  case object LookupKeyValue extends KeyValueCommand

  trait KeyValueCommandResult extends CommandResult

  @SerialVersionUID(0L)
  case class KeyValueFound(value: String) extends KeyValueCommandResult

  case object KeyValueAdded extends KeyValueCommandResult

  case object KeyValueRemoved extends KeyValueCommandResult

  @SerialVersionUID(0L)
  class KeyValueErrorMessage(override val message: String) extends ErrorMessage(message)
    with KeyValueCommandResult

  case object KeyValueNotFound extends KeyValueErrorMessage("KeyValueNotFound")

  sealed trait KeyValueEvent extends Event

  case class KeyValueAddedEvent(key: String, value: String) extends KeyValueEvent

  case class KeyValueRemovedEvent(key: String) extends KeyValueEvent

}
