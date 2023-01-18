package app.softnetwork.counter.handlers

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ddata.PNCounterKey
import app.softnetwork.persistence.typed.scaladsl.SingletonPattern
import app.softnetwork.counter.message._
import app.softnetwork.counter.persistence.data.Counter

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions

/** Created by smanciot on 28/03/2021.
  */
trait CounterDao extends SingletonPattern[CounterCommand, CounterResult] {

  implicit def command2Request(command: CounterCommand): Request = replyTo =>
    CounterCommandWrapper(command, replyTo)

  override lazy val behavior: Behavior[CounterCommand] = Counter(PNCounterKey(name))

  override lazy val key: ServiceKey[CounterCommand] = ServiceKey[CounterCommand](name)

  def inc()(implicit system: ActorSystem[_]): Future[Either[CounterResult, Int]] = {
    this ! IncrementCounter
    load()
  }

  def dec()(implicit system: ActorSystem[_]): Future[Either[CounterResult, Int]] = {
    this ! DecrementCounter
    load()
  }

  def reset(value: Int = 0)(implicit system: ActorSystem[_]): Future[Either[CounterResult, Int]] = {
    this ! ResetCounter(value)
    load()
  }

  def load()(implicit system: ActorSystem[_]): Future[Either[CounterResult, Int]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    (this ? GetCounterValue).map {
      case r: CounterLoaded => Right(r.value)
      case other            => Left(other)
    }
  }

  def loadFromCache()(implicit system: ActorSystem[_]): Future[Either[CounterResult, Int]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    (this ? GetCounterCachedValue).map {
      case r: CounterLoaded => Right(r.value)
      case other            => Left(other)
    }
  }

}

object CounterDao {
  def apply(counter: String): CounterDao = new CounterDao() {
    override lazy val name: String = counter
  }
}
