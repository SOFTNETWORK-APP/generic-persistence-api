package app.softnetwork.kv.handlers

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.ServiceKey
import app.softnetwork.kv.message.{KvCommandResult, KvFound}
import app.softnetwork.kv.persistence.data.Kv
import Kv._
import app.softnetwork.persistence.typed.scaladsl.SingletonPattern
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions
import scala.language.reflectiveCalls

/** Created by smanciot on 02/06/2020.
  */
trait Kv2Dao extends SingletonPattern[KvCommand, KvCommandResult] with GenericKeyValueDao {
  override implicit def command2Request(command: KvCommand): Request = replyTo =>
    KvCommandWrapper(command, replyTo)

  def log: Logger

  override lazy val behavior: Behavior[KvCommand] = Kv(name)

  override lazy val key: ServiceKey[KvCommand] = ServiceKey(name)

  def lookupKeyValue(key: String)(implicit system: ActorSystem[_]): Future[Option[String]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this ? Lookup(key) map {
      case r: KvFound => Some(r.value)
      case _          => None
    }
  }

  def addKeyValue(key: String, value: String)(implicit system: ActorSystem[_]): Unit = {
    log.info(s"adding ($key, $value)")
    this ! Put(key, value)
  }

  def removeKeyValue(key: String)(implicit system: ActorSystem[_]): Unit = {
    log.info(s"removing ($key)")
    this ! Remove(key)
  }

}

object Kv2Dao {
  def apply(namespace: String): Kv2Dao = new Kv2Dao {
    override lazy val name: String = namespace
    lazy val log: Logger = LoggerFactory getLogger getClass.getName
  }
}
