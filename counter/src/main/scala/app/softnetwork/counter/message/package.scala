package app.softnetwork.counter

import akka.actor.typed.ActorRef

import app.softnetwork.persistence.message.{Command, CommandResult, CommandWrapper}

/** Created by smanciot on 28/03/2021.
  */
package object message {

  sealed trait CounterCommand extends Command

  case class CounterCommandWrapper(command: CounterCommand, replyTo: ActorRef[CounterResult])
      extends CommandWrapper[CounterCommand, CounterResult]
      with CounterCommand

  trait InternalCounterCommand extends CounterCommand

  case object IncrementCounter extends CounterCommand

  case object DecrementCounter extends CounterCommand

  case class ResetCounter(value: Int = 0) extends CounterCommand

  case object GetCounterValue extends CounterCommand

  case object GetCounterCachedValue extends CounterCommand

  case object UnsubscribeCounter extends CounterCommand

  sealed trait CounterResult extends CommandResult

  case class CounterLoaded(value: Int) extends CounterResult

}
