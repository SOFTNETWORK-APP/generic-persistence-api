package app.softnetwork.kv.persistence.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.kv.message._
import app.softnetwork.kv.model.KvState
import app.softnetwork.persistence.typed._

trait KvBehavior[S <: KvState]  extends EntityBehavior[
  KvCommand,
  S,
  KvEvent,
  KvCommandResult]{

  def createKv(key: String, value: String): S

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
                              state: Option[S],
                              command: KvCommand,
                              replyTo: Option[ActorRef[KvCommandResult]],
                              timers: TimerScheduler[KvCommand])(
                              implicit context: ActorContext[KvCommand]
                            ): Effect[KvEvent, Option[S]] = {
    command match {

      case cmd: Put =>
        Effect.persist(
          KvAddedEvent(entityId, cmd.value)
        ).thenRun(
          _ => KvAdded ~> replyTo
        )

      case Remove =>
        Effect.persist(
          KvRemovedEvent(
            entityId
          )
        ).thenRun(
          _ => KvRemoved ~> replyTo
        )//.thenStop()

      case Lookup =>
        state match {
          case Some(s) => Effect.none.thenRun(_ => KvFound(s.value) ~> replyTo)
          case _       => Effect.none.thenRun(_ => KvNotFound ~> replyTo)
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
  override def handleEvent(state: Option[S], event: KvEvent)(
    implicit context: ActorContext[_]): Option[S] = {
    event match {
      case e: KvAddedEvent => Some(createKv(e.key, e.value))
      case _: KvRemovedEvent => emptyState
      case _ => super.handleEvent(state, event)
    }
  }
}

