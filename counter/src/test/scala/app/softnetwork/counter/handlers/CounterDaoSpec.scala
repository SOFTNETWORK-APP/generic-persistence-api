package app.softnetwork.counter.handlers

import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
  * Created by smanciot on 29/03/2021.
  */
class CounterDaoSpec extends AnyWordSpecLike with InMemoryPersistenceTestKit with Matchers {

  val counterDao = CounterDao("counter")

  implicit lazy val system = typedSystem()

  implicit lazy val ec = system.executionContext

  "Counter handler" must {
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
