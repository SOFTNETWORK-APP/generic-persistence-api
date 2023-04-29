package app.softnetwork.persistence.message

import akka.actor.typed.ActorRef

object SampleMessages {

  sealed trait SampleCommand extends Command

  case class SampleCommandWrapper(command: SampleCommand, replyTo: ActorRef[SampleCommandResult])
      extends CommandWrapper[SampleCommand, SampleCommandResult]
      with SampleCommand

  case object TestSample extends SampleCommand

  sealed trait SampleCommandResult extends CommandResult

  case object SampleTested extends SampleCommandResult

}
