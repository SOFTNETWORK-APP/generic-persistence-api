package app.softnetwork.counter.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.Singleton
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor

/** Created by smanciot on 29/03/2021.
  */
class CounterDaoSpec extends AnyWordSpecLike with InMemoryPersistenceTestKit with Matchers {

  val counterDao: CounterDao = CounterDao("counter")

  implicit lazy val system: ActorSystem[_] = typedSystem()

  implicit lazy val ec: ExecutionContextExecutor = system.executionContext

  /** initialize all singletons
    */
  override def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq(counterDao)

  "CounterDao" must {
    "increments counter" in {
      counterDao.inc() assert (_.right.get shouldBe 1)
    }
    "decrements counter" in {
      counterDao.dec() assert (_.right.get shouldBe 0)
    }
    "resets counter" in {
      counterDao.reset(100) assert (_.right.get shouldBe 100)
    }
  }
}
