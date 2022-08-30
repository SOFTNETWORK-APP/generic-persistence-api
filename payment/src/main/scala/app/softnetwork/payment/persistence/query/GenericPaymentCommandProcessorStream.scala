package app.softnetwork.payment.persistence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.payment.handlers.{GenericPaymentHandler, MangoPayPaymentHandler, MockPaymentHandler}
import app.softnetwork.payment.message.PaymentEvents._
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.config.Settings
import app.softnetwork.persistence.query.{EventProcessorStream, JournalProvider}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait GenericPaymentCommandProcessorStream extends EventProcessorStream[PaymentCommandEvent]{
  _: JournalProvider with GenericPaymentHandler =>

  override lazy val tag: String = Settings.ExternalToPaymentAccountTag

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
      case evt: WrapPaymentCommandEvent =>
        val promise = Promise[Done]
        processEvent(evt.event, persistenceId, sequenceNr) onComplete {
          case Success(_) => promise.success(Done)
          case Failure(f) =>
            logger.error(f.getMessage)
            promise.failure(f)
        }
        promise.future
      case evt: CreateOrUpdatePaymentAccountCommandEvent =>
        val command = CreateOrUpdatePaymentAccount(evt.paymentAccount)
        !? (command) map {
          case PaymentAccountCreated =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case PaymentAccountUpdated =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: PayInWithCardPreAuthorizedCommandEvent =>
        import evt._
        val command = PayInWithCardPreAuthorized(preAuthorizationId, creditedAccount)
        !? (command) map {
          case _: PaidInResult =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: RefundCommandEvent =>
        import evt._
        val command = Refund(orderUuid, payInTransactionId, refundAmount, currency, reasonMessage, initializedByClient)
        !? (command) map {
          case _: Refunded =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: PayOutCommandEvent =>
        import evt._
        val command = PayOut(orderUuid, creditedAccount, creditedAmount, feesAmount, currency)
        !? (command) map {
          case _: PaidOut =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: TransferCommandEvent =>
        import evt._
        val command = Transfer(orderUuid, debitedAccount, creditedAccount, debitedAmount, feesAmount, currency, payOutRequired, externalReference)
        !? (command) map {
          case _: Transfered =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: DirectDebitCommandEvent =>
        import evt._
        val command = DirectDebit(creditedAccount, debitedAmount, feesAmount, currency, statementDescriptor, externalReference)
        !? (command) map {
          case _: DirectDebited =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: LoadDirectDebitTransactionCommandEvent =>
        import evt._
        val command = LoadDirectDebitTransaction(directDebitTransactionId)
        !? (command) map {
          case _: DirectDebited =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: RegisterRecurringPaymentCommandEvent =>
        import evt._
        val command = RegisterRecurringPayment(debitedAccount, firstDebitedAmount, firstFeesAmount, currency, `type`, startDate, endDate, frequency, fixedNextAmount, nextDebitedAmount, nextFeesAmount)
        !? (command) map {
          case _: RecurringPaymentRegistered =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: CancelPreAuthorizationCommandEvent =>
        import evt._
        val command = CancelPreAuthorization(orderUuid, cardPreAuthorizedTransactionId)
        !? (command) map {
          case _: PreAuthorizationCanceled =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: CancelMandateCommandEvent =>
        import evt._
        val command = CancelMandate(externalUuid)
        !? (command) map {
          case MandateCanceled =>
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