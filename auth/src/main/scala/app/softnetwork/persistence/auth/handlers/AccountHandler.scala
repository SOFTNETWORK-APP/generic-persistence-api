package app.softnetwork.persistence.auth.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.typed.CommandTypeKey
import org.softnetwork.notification.model.NotificationType
import app.softnetwork.persistence.auth.message._
import app.softnetwork.persistence.auth.persistence.typed.{MockBasicAccountBehavior, BasicAccountBehavior}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

/**
  * Created by smanciot on 18/04/2020.
  */
trait AccountHandler extends EntityPattern[AccountCommand, AccountCommandResult] {_: CommandTypeKey[AccountCommand] =>

  private[this] val accountKeyDao = AccountKeyDao

  override protected def lookup[T](key: T)(implicit system: ActorSystem[_]): Future[Option[String]] = {
    implicit val ec = system.executionContext
    accountKeyDao.lookupAccount(key)
  }

  override def ??[T](key: T, command: AccountCommand)(
    implicit tTag: ClassTag[AccountCommand], system: ActorSystem[_]): Future[AccountCommandResult] = {
      implicit val ec = system.executionContext
      command match {
        case _: LookupAccountCommand => lookup(key) flatMap {
          case Some(entityId) => this ? (entityId, command)
          case _ =>
            command match {
              case _: CheckResetPasswordToken => Future.successful(TokenNotFound)
              case _: ResetPassword => Future.successful(CodeNotFound)
              case _: Login => Future.successful(LoginAndPasswordNotMatched)
              case _ => Future.successful(AccountNotFound)
            }
        }
        case _ => this ? (key, command)
      }
    }

}

trait AccountDao { _: AccountHandler =>

  import app.softnetwork.persistence._

  def initAdminAccount(login: String, password: String)(implicit system: ActorSystem[_]) = { // FIXME
    this ! (generateUUID(Some(login)), new InitAdminAccount(login, password)) /*match {
      case AdminAccountInitialized => true
      case _ => false
    }*/
  }

  def recordNotification(uuids: Set[String], channel: NotificationType, subject: String, content: String)(implicit system: ActorSystem[_]) = {
    implicit val ec = system.executionContext
    this ? (ALL_KEY, RecordNotification(uuids, channel, subject, content)) map {
      case NotificationRecorded => true
      case other => false
    }
  }
}

trait BasicAccountTypeKey extends CommandTypeKey[AccountCommand]{
  override def TypeKey(implicit tTag: ClassTag[AccountCommand]) = BasicAccountBehavior.TypeKey
}

trait MockBasicAccountTypeKey extends CommandTypeKey[AccountCommand]{
  override def TypeKey(implicit tTag: ClassTag[AccountCommand]) = MockBasicAccountBehavior.TypeKey
}

object BasicAccountDao extends AccountDao with AccountHandler with BasicAccountTypeKey

object MockBasicAccountHandler extends AccountHandler with MockBasicAccountTypeKey

