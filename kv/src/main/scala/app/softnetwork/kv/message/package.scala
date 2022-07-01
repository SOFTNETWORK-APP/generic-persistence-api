package app.softnetwork.kv

import app.softnetwork.persistence.message.{Command, CommandResult, ErrorMessage, Event}

package object message {

  sealed trait KvCommand extends Command

  @SerialVersionUID(0L)
  case class Put(value: String) extends KvCommand

  case object Remove extends KvCommand

  case object Lookup extends KvCommand

  trait KvCommandResult extends CommandResult

  @SerialVersionUID(0L)
  case class KvFound(value: String) extends KvCommandResult

  case object KvAdded extends KvCommandResult

  case object KvRemoved extends KvCommandResult

  @SerialVersionUID(0L)
  class KvErrorMessage(override val message: String) extends ErrorMessage(message)
    with KvCommandResult

  case object KvNotFound extends KvErrorMessage("KvNotFound")

}
