package app.softnetwork.sequence.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed._
import app.softnetwork.sequence.persistence.typed.Sequence
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.sequence.message._

import scala.concurrent.ExecutionContextExecutor

/**
  * Created by smanciot on 19/03/2020.
  */
class SequenceHandlerSpec extends SequenceHandler with AnyWordSpecLike with InMemoryPersistenceTestKit {

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: (ActorSystem[_]) => Seq[EntityBehavior[_, _, _, _]] = _ => List(
    Sequence
  )

  implicit lazy val system: ActorSystem[Nothing] = typedSystem()

  implicit lazy val ec: ExecutionContextExecutor = system.executionContext

  "Sequence" must {
    "handle Inc" in {
      this !! IncSequence("inc")
      this !? LoadSequence("inc") assert {
        case s: SequenceLoaded => s.value shouldBe 1
        case _                       => fail()
      }
      SequenceDao.inc("inc") complete()
      this !? LoadSequence("inc") assert {
        case s: SequenceLoaded => s.value shouldBe 2
        case _                       => fail()
      }
    }
    "handle Dec" in {
      this !! IncSequence("dec")
      this !! IncSequence("dec")
      this !! DecSequence("dec")
      this !? LoadSequence("dec") assert {
        case s: SequenceLoaded => s.value shouldBe 1
        case _                       => fail()
      }
    }
    "handle Reset" in {
      this !! IncSequence("reset")
      this !! IncSequence("reset")
      this !! ResetSequence("reset")
      this !? LoadSequence("reset") assert {
        case s: SequenceLoaded => s.value shouldBe 0
        case _                       => fail()
      }
    }
    "handle Load" in {
      this !! IncSequence("load")
      this !? LoadSequence("load") assert {
        case s: SequenceLoaded => s.value shouldBe 1
        case _                       => fail()
      }
    }
  }
}
