package app.softnetwork.sequence

import app.softnetwork.persistence.message.{CommandResult, EntityCommand}

/**
  * Created by smanciot on 15/03/2021.
  */
package object message {

  sealed trait SequenceCommand extends EntityCommand {
    def sequence: String
    override val id: String = sequence
  }

  @SerialVersionUID(0L)
  case class IncSequence(sequence: String) extends SequenceCommand

  @SerialVersionUID(0L)
  case class DecSequence(sequence: String) extends SequenceCommand

  @SerialVersionUID(0L)
  case class ResetSequence(sequence: String) extends SequenceCommand

  @SerialVersionUID(0L)
  case class LoadSequence(sequence: String) extends SequenceCommand

  trait SequenceResult extends CommandResult

  case object SequenceNotFound extends SequenceResult

}
