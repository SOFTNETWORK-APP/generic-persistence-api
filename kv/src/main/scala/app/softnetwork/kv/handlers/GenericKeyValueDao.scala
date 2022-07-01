package app.softnetwork.kv.handlers

import akka.actor.typed.ActorSystem

import scala.concurrent.Future

trait GenericKeyValueDao {
  def lookupKeyValue(key: String)(implicit system: ActorSystem[_]): Future[Option[String]]
  def addKeyValue(key: String, value: String)(implicit system: ActorSystem[_]): Unit
  def removeKeyValue(key: String)(implicit system: ActorSystem[_]): Unit
}
