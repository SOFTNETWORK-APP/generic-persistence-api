package app.softnetwork.payment.persistence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.payment.handlers.{GenericPaymentHandler, MangoPayPaymentHandler, MockPaymentHandler}
import app.softnetwork.payment.message.PaymentEvents._
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.persistence.query.{EventProcessorStream, JournalProvider}

import scala.concurrent.Future

trait GenericPaymentCommandProcessorStream extends EventProcessorStream[PaymentCommandEvent]{
  _: JournalProvider with GenericPaymentHandler =>

  /**
    *
    * @return whether or not the events processed by this processor stream would be published to the main bus event
    */
  def forTests: Boolean = false

  /**
    *
    * Processing event
    *
    * @param event         - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr    - sequence number
    * @return
    */
  override protected def processEvent(event: PaymentCommandEvent, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    event match {
      case evt: WrapPaymentCommandEvent => processEvent(evt.event, persistenceId, sequenceNr)
      case evt: PayInWithCardPreAuthorizedCommandEvent =>
        import evt._
        val command = PayInWithCardPreAuthorized(preAuthorizationId, creditedAccount)
        ? (command) map {
          case _: PaidInResult =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: RefundCommandEvent =>
        import evt._
        val command = Refund(orderUuid, payInTransactionId, refundAmount, reasonMessage, initializedByClient)
        ? (command) map {
          case _: Refunded =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: PayOutCommandEvent =>
        import evt._
        val command = PayOut(orderUuid, creditedAccount, creditedAmount, feesAmount)
        ? (command) map {
          case _: PaidOut =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: TransferCommandEvent =>
        import evt._
        val command = Transfer(orderUuid, debitedAccount, creditedAccount, debitedAmount, feesAmount, payOutRequired)
        ? (command) map {
          case _: Transfered =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case other =>
        logger.warn(s"$platformEventProcessorId does not support event [${other.getClass}]")
        Future.successful(Done)
    }
  }
}

trait MangoPayPaymentCommandProcessorStream extends GenericPaymentCommandProcessorStream with MangoPayPaymentHandler {
  _: JournalProvider =>
}

trait MockPaymentCommandProcessorStream extends GenericPaymentCommandProcessorStream with MockPaymentHandler {
  _: JournalProvider =>
}