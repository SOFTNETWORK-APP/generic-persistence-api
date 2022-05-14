package app.softnetwork.payment.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.payment.message.TransactionEvents._
import app.softnetwork.payment.message.TransactionMessages._
import app.softnetwork.payment.model.Transaction
import app.softnetwork.payment.spi._
import app.softnetwork.persistence.typed._

/**
  * Created by smanciot on 09/05/2020.
  */
trait TransactionBehavior extends PaymentBehavior[TransactionCommand, Transaction, TransactionEvent, TransactionResult] {
  _: PaymentProvider =>

  override protected val manifestWrapper: ManifestW = ManifestW()

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event    - the event to tag
    * @return event tags
    */
  override protected def tagEvent(entityId: String, event: TransactionEvent): Set[String] =
    event match {
      case _ => super.tagEvent(entityId, event)
    }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(entityId: String, state: Option[Transaction], command: TransactionCommand,
                             replyTo: Option[ActorRef[TransactionResult]], timers: TimerScheduler[TransactionCommand])(
    implicit context: ActorContext[TransactionCommand]
  ): Effect[TransactionEvent, Option[Transaction]] =

    command match {

      case cmd: RecordTransaction =>
        import cmd._
        val event =
          TransactionCreatedEvent(
            transaction
          )
        Effect.persist(event).thenRun(_ => TransactionRecorded ~> replyTo)

      case LoadTransaction =>
        state match {
          case Some(transaction) => Effect.none.thenRun(_ => TransactionLoaded(transaction) ~> replyTo)
          case _ => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
        }

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[Transaction], event: TransactionEvent)(
    implicit context: ActorContext[_]): Option[Transaction] =
    event match {
      case _ => super.handleEvent(state, event)
    }

}

case object TransactionBehavior extends TransactionBehavior with MangoPayProvider

case object MockTransactionBehavior extends TransactionBehavior with MockMangoPayProvider{
  override def persistenceId = s"Mock${super.persistenceId}"
}
