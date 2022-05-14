package app.softnetwork.payment.message

import app.softnetwork.payment.model.Transaction

import app.softnetwork.persistence.message._

/**
  * Created by smanciot on 10/05/2020.
  */
object TransactionMessages {

  sealed trait TransactionCommand extends Command

  case class RecordTransaction(transaction: Transaction) extends TransactionCommand

  case object LoadTransaction extends TransactionCommand

  sealed trait TransactionResult extends CommandResult

  case object TransactionRecorded extends TransactionResult

  case class TransactionLoaded(transaction: Transaction) extends TransactionResult

  class TransactionError(override val message: String) extends ErrorMessage(message) with TransactionResult

  case object TransactionNotRecorded extends TransactionError("TransactionNotRecorded")

  case object TransactionNotFound extends TransactionError("TransactionNotFound")
}
