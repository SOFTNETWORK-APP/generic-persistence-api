package app.softnetwork.persistence.auth.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.kv.handlers.{KvDao, KvHandler}
import app.softnetwork.persistence.auth.persistence.typed.AccountKeyBehavior

import scala.concurrent.Future

/**
  * Created by smanciot on 17/04/2020.
  */
trait AccountKeyHandler extends KvHandler with AccountKeyBehavior

object AccountKeyHandler extends AccountKeyHandler

trait AccountKeyDao extends KvDao {_: KvHandler =>
  def lookupAccount(key: String)(implicit system: ActorSystem[_]): Future[Option[String]] = {
    lookupKeyValue(key)
  }

  def addAccountKey(key: String, account: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"adding ($key, $account)")
    addKeyValue(key, account)
  }

  def removeAccountKey(key: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"removing ($key)")
    removeKeyValue(key)
  }

}

object AccountKeyDao extends AccountKeyDao with AccountKeyHandler
