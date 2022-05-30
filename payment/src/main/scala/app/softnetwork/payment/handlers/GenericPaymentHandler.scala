package app.softnetwork.payment.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.kv.handlers.KeyValueDao
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.model._
import app.softnetwork.persistence._
import app.softnetwork.payment.persistence.typed.{MangoPayPaymentBehavior, MockPaymentBehavior}
import app.softnetwork.persistence.typed.CommandTypeKey

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * Created by smanciot on 22/04/2022.
  */
trait MangoPayPaymentTypeKey extends CommandTypeKey[PaymentCommand]{
  override def TypeKey(implicit tTag: ClassTag[PaymentCommand]): EntityTypeKey[PaymentCommand] =
    MangoPayPaymentBehavior.TypeKey
}

trait MockPaymentTypeKey extends CommandTypeKey[PaymentCommand]{
  override def TypeKey(implicit tTag: ClassTag[PaymentCommand]): EntityTypeKey[PaymentCommand] =
    MockPaymentBehavior.TypeKey
}

trait GenericPaymentHandler extends EntityPattern[PaymentCommand, PaymentResult] {_:  CommandTypeKey[PaymentCommand] =>
  lazy val keyValueDao: KeyValueDao = KeyValueDao

  protected override def lookup[T](key: T)(implicit system: ActorSystem[_]): Future[Option[Recipient]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val promise = Promise[Option[Recipient]]
    keyValueDao.lookupKeyValue(key) onComplete {
      case Success(value) =>
        value match {
          case None => promise.success(Some(generateUUID(Some(key))))
          case some => promise.success(some)
        }
      case Failure(_) => promise.success(Some(generateUUID(Some(key))))
    }
    promise.future
  }

  def ?(command: PaymentCommandWithKey)(implicit tTag: ClassTag[PaymentCommandWithKey], system: ActorSystem[_]
  ): Future[PaymentResult] = super.??(command.key, command)

  def !(command: PaymentCommandWithKey)(implicit tTag: ClassTag[PaymentCommandWithKey], system: ActorSystem[_]): Unit =
    super.?!(command.key, command)

}

trait MangoPayPaymentHandler extends GenericPaymentHandler with MangoPayPaymentTypeKey

object MangoPayPaymentHandler extends MangoPayPaymentHandler

trait MockPaymentHandler extends GenericPaymentHandler with MockPaymentTypeKey

object MockPaymentHandler extends MockPaymentHandler

trait GenericPaymentDao{ _: GenericPaymentHandler =>

  protected[payment] def loadPaymentAccount(key: String)(implicit system: ActorSystem[_]
  ): Future[Option[PaymentAccount]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (LoadPaymentAccount(key)) map {
      case result: PaymentAccountLoaded => Some(result.paymentAccount)
      case _ => None
    }
  }

  def createOrUpdatePaymentAccount(paymentAccount: PaymentAccount)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    if(paymentAccount.externalUuid.trim.isEmpty){
      Future.successful(false)
    }
    else{
      ? (CreateOrUpdatePaymentAccount(paymentAccount)) map {
        case PaymentAccountCreated => true
        case PaymentAccountUpdated => true
        case _ => false
      }
    }
  }

  def payInWithCardPreAuthorized(preAuthorizationId: String, creditedAccount: String)(implicit system: ActorSystem[_]
  ): Future[Either[PayInFailed, PaidInResult]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (PayInWithCardPreAuthorized(preAuthorizationId, creditedAccount)) map {
      case result: PaidInResult => Right(result)
      case error: PayInFailed => Left(error)
      case _ => Left(PayInFailed("unknown"))
    }
  }

  def refund(orderUuid: String,
             payInTransactionId: String,
             refundAmount: Int,
             reasonMessage: String,
             initializedByClient: Boolean)(implicit system: ActorSystem[_]): Future[Either[RefundFailed, Refunded]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (Refund(orderUuid, payInTransactionId, refundAmount, reasonMessage, initializedByClient)) map {
      case result: Refunded => Right(result)
      case error: RefundFailed => Left(error)
      case _ => Left(RefundFailed("unknown"))
    }
  }

  def payOut(orderUuid: String,
             creditedAccount: String,
             creditedAmount: Int,
             feesAmount: Int)(implicit system: ActorSystem[_]): Future[Either[PayOutFailed, PaidOut]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (PayOut(orderUuid, creditedAccount, creditedAmount, feesAmount)) map {
      case result: PaidOut => Right(result)
      case error: PayOutFailed => Left(error)
      case _ => Left(PayOutFailed("unknown"))
    }
  }

  def transfer(orderUuid: Option[String] = None,
               debitedAccount: String,
               creditedAccount: String,
               debitedAmount: Int,
               feesAmount: Int = 0,
               payOutRequired: Boolean = true)(implicit system: ActorSystem[_]
  ): Future[Either[TransferFailed, Transfered]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    ? (Transfer(orderUuid, debitedAccount, creditedAccount, debitedAmount, feesAmount, payOutRequired)) map {
      case result: Transfered => Right(result)
      case error: TransferFailed => Left(error)
      case _ => Left(TransferFailed("unknown"))
    }
  }
}

object MangoPayPaymentDao extends GenericPaymentDao with MangoPayPaymentHandler

object MockPaymentDao extends GenericPaymentDao with MockPaymentHandler
