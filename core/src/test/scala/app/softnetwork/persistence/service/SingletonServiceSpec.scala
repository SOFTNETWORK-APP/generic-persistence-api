package app.softnetwork.persistence.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed.scaladsl._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.{Failure, Success}

/**
  * Created by smanciot on 02/06/2021.
  */
class SingletonServiceSpec extends SingletonService[SampleCommand, SampleCommandResult]
  with SamplePattern
  with AnyWordSpecLike
  with Completion {

  lazy val systemName: String = generateUUID()

  private[this] lazy val testKit = ActorTestKit(systemName)

  implicit lazy val system: ActorSystem[Nothing] = testKit.system

  def test() = run(TestSample) complete() match {
    case Success(s) => s match {
      case SampleTested => logger.info("sample tested !")
      case other => fail(other.toString)
    }
    case Failure(f) => fail(f.getMessage)
  }

  "SingletonService" must {
    "run commands" in {
      test()
      test()
    }
  }
}
