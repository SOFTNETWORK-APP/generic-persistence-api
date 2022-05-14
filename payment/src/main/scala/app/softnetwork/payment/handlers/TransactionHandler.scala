package app.softnetwork.payment.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.payment.message.TransactionMessages._
import app.softnetwork.payment.model.Transaction
import app.softnetwork.payment.persistence.typed.{MockTransactionBehavior, TransactionBehavior}
import app.softnetwork.persistence.typed.CommandTypeKey

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Created by smanciot on 09/05/2020.
  */
trait TransactionTypeKey extends CommandTypeKey[TransactionCommand]{
  override def TypeKey(implicit tTag: ClassTag[TransactionCommand]): EntityTypeKey[TransactionCommand] =
    TransactionBehavior.TypeKey
}

trait MockTransactionTypeKey extends CommandTypeKey[TransactionCommand]{
  override def TypeKey(implicit tTag: ClassTag[TransactionCommand]): EntityTypeKey[TransactionCommand] =
    MockTransactionBehavior.TypeKey
}

trait TransactionHandler extends EntityPattern[TransactionCommand, TransactionResult] with TransactionTypeKey

trait MockTransactionHandler extends TransactionHandler with MockTransactionTypeKey

object MockTransactionHandler extends MockTransactionHandler

trait TransactionDao{ _: TransactionHandler =>

  def record(transaction: Transaction)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (transaction.uuid, RecordTransaction(transaction)) map {
      case TransactionRecorded => true
      case _ => false
    }
  }

  def load(transactionId: String)(implicit system: ActorSystem[_]): Future[Option[Transaction]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (transactionId, LoadTransaction) map {
      case r: TransactionLoaded => Some(r.transaction)
      case _ => None
    }
  }
}

object TransactionDao extends TransactionDao with TransactionHandler

object MockTransactionDao extends TransactionDao with MockTransactionHandler
