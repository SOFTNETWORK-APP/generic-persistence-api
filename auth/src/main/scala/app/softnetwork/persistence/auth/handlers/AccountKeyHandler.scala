package app.softnetwork.persistence.auth.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.typed.scaladsl.EntityPattern

import app.softnetwork.persistence.auth.message._

import app.softnetwork.persistence.auth.persistence.typed.AccountKeyBehavior

import app.softnetwork.persistence._

import scala.concurrent.Future

/**
  * Created by smanciot on 17/04/2020.
  */
trait AccountKeyHandler extends EntityPattern[AccountKeyCommand, AccountKeyCommandResult] with AccountKeyBehavior {

//  implicit def command2Request(command: AccountKeyCommand): Request =
//    replyTo => AccountKeyCommandWrapper(command, replyTo)

}

object AccountKeyHandler extends AccountKeyHandler

trait AccountKeyDao {_: AccountKeyHandler =>

  def lookupAccount(key: String)(implicit system: ActorSystem[_]): Future[Option[String]] = {
    implicit val ec = system.executionContext
    this ? (generateUUID(Some(key)), LookupAccountKey) map {
      case r: AccountKeyFound =>
        import r._
        logger.info(s"found $account for $key")
        Some(account)
      case _                  =>
        logger.warn(s"could not find an account for $key")
        None
    }
  }

  def addAccountKey(key: String, account: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"adding ($key, $account)")
    this ! (generateUUID(Some(key)), AddAccountKey(account))
  }

  def removeAccountKey(key: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"removing ($key)")
    this ! (generateUUID(Some(key)), RemoveAccountKey)
  }

}

object AccountKeyDao extends AccountKeyDao with AccountKeyHandler
