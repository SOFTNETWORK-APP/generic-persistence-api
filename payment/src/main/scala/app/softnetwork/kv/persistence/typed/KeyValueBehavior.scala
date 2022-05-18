package app.softnetwork.kv.persistence.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.kv.message._
import app.softnetwork.kv.model.KeyValue
import app.softnetwork.persistence.typed._

trait KeyValueBehavior extends EntityBehavior[
  KeyValueCommand,
  KeyValue,
  KeyValueEvent,
  KeyValueCommandResult]{

  override def persistenceId: String = "KeyValue"

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(
                              entityId: String,
                              state: Option[KeyValue],
                              command: KeyValueCommand,
                              replyTo: Option[ActorRef[KeyValueCommandResult]],
                              timers: TimerScheduler[KeyValueCommand])(
                              implicit context: ActorContext[KeyValueCommand]
                            ): Effect[KeyValueEvent, Option[KeyValue]] = {
    command match {

      case cmd: AddKeyValue =>
        Effect.persist(
          KeyValueAddedEvent(entityId, cmd.value)
        ).thenRun(
          _ => KeyValueAdded ~> replyTo
        )

      case RemoveKeyValue =>
        Effect.persist(
          KeyValueRemovedEvent(
            entityId
          )
        ).thenRun(
          _ => KeyValueRemoved ~> replyTo
        )//.thenStop()

      case LookupKeyValue =>
        state match {
          case Some(s) => Effect.none.thenRun(_ => KeyValueFound(s.value) ~> replyTo)
          case _       => Effect.none.thenRun(_ => KeyValueNotFound ~> replyTo)
        }

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[KeyValue], event: KeyValueEvent)(
    implicit context: ActorContext[_]): Option[KeyValue] = {
    event match {
      case e: KeyValueAddedEvent => Some(KeyValue(e.key, e.value))
      case _: KeyValueRemovedEvent => emptyState
      case _ => super.handleEvent(state, event)
    }
  }
}

object KeyValueBehavior extends KeyValueBehavior
