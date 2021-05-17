package app.softnetwork.counter.handlers

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ddata.PNCounterKey
import app.softnetwork.persistence.typed.scaladsl.BehaviorPattern
import app.softnetwork.counter.message._
import app.softnetwork.counter.persistence.data.Counter

import scala.language.implicitConversions

/**
  * Created by smanciot on 28/03/2021.
  */
trait CounterDao extends BehaviorPattern[CounterCommand, CounterResult]{

  implicit def command2Request(command: CounterCommand): Request = replyTo =>
    new CounterCommandWrapper(command, replyTo)

  override lazy val singleton: Behavior[CounterCommand] = Counter(PNCounterKey(name))

  def inc()(implicit system: ActorSystem[_])  = {
    this ! IncrementCounter
    load()
  }

  def dec()(implicit system: ActorSystem[_])  = {
    this ! DecrementCounter
    load()
  }

  def reset(value: Int = 0)(implicit system: ActorSystem[_])  = {
    this ! ResetCounter(value)
    load()
  }

  def load()(implicit system: ActorSystem[_])  = {
    implicit val ec = system.executionContext
    (this ? GetCounterValue).map {
      case r: CounterLoaded => Right(r.value)
      case other => Left(other)
    }
  }

  def loadFromCache()(implicit system: ActorSystem[_])  = {
    implicit val ec = system.executionContext
    (this ? GetCounterCachedValue).map {
      case r: CounterLoaded => Right(r.value)
      case other => Left(other)
    }
  }

}

object CounterDao{
  def apply(counter: String): CounterDao = new CounterDao() {
    override protected def name: String = counter
  }
}