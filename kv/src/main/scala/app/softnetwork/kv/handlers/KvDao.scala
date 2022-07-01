package app.softnetwork.kv.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.kv.message._
import app.softnetwork.kv.persistence.typed.KeyValueBehavior
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed.CommandTypeKey

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Created by smanciot on 17/04/2020.
  */
trait KvHandler extends EntityPattern[KvCommand, KvCommandResult] {_: CommandTypeKey[KvCommand] =>
}

trait KvDao extends GenericKeyValueDao {_: KvHandler =>

  def lookupKeyValue(key: String)(implicit system: ActorSystem[_]): Future[Option[String]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this ? (generateUUID(Some(key)), Lookup) map {
      case r: KvFound =>
        import r._
        logger.info(s"found $value for $key")
        Some(value)
      case _ =>
        logger.warn(s"could not find a value for $key")
        None
    }
  }

  def addKeyValue(key: String, value: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"adding ($key, $value)")
    this ! (generateUUID(Some(key)), Put(value))
  }

  def removeKeyValue(key: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"removing ($key)")
    this ! (generateUUID(Some(key)), Remove)
  }

}
