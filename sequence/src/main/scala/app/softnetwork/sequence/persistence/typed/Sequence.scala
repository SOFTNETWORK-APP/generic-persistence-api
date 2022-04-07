package app.softnetwork.sequence.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.persistence.typed._
import app.softnetwork.sequence.message._
import app.softnetwork.sequence.model._

import scala.language.implicitConversions

/**
  * Created by smanciot on 18/03/2020.
  */
trait Sequence extends EntityBehavior[SequenceCommand, SequenceState, SequenceEvent, SequenceResult]{
  val persistenceId = "Sequence"

  private[this] def value(state: Option[SequenceState]): Int = state.map(_.value).getOrElse(0)

  override def handleCommand( entityId: String,
                              state: Option[SequenceState],
                              command: SequenceCommand,
                              replyTo: Option[ActorRef[SequenceResult]],
                              timers: TimerScheduler[SequenceCommand])(implicit context: ActorContext[SequenceCommand]
  ): Effect[SequenceEvent, Option[SequenceState]] = {
    command match {

      case _: IncSequence   => Effect.persist(SequenceIncremented(entityId, state.map(_.value+1).getOrElse(1)))
        .thenRun(state => SequenceIncremented(entityId, value(state)) ~> replyTo)

      case _: DecSequence   => Effect.persist(SequenceDecremented(entityId, state.map(_.value-1).getOrElse(0)))
        .thenRun(state => SequenceDecremented(entityId, value(state)) ~> replyTo)

      case _: ResetSequence => Effect.persist(SequenceResetted(entityId))
        .thenRun(_ => SequenceResetted(entityId) ~> replyTo)

      case _: LoadSequence  => Effect.none
        .thenRun(state => SequenceLoaded(entityId, value(state)) ~> replyTo)

      case _                => Effect.unhandled
    }
  }

  override def handleEvent(state: Option[SequenceState], event: SequenceEvent)(
    implicit context: ActorContext[_]): Option[SequenceState] = {
    event match {
      case e: SequenceIncremented => Some(SequenceState(e.name, e.value))
      case e: SequenceDecremented => Some(SequenceState(e.name, e.value))
      case _: SequenceResetted    => emptyState
      case _                      => state
    }
  }
}

object Sequence extends Sequence
