package app.softnetwork.persistence.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence._
import app.softnetwork.persistence.message.SampleMessages.{
  SampleCommand,
  SampleCommandResult,
  SampleTested,
  TestSample
}
import app.softnetwork.persistence.typed.scaladsl._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success}

/** Created by smanciot on 02/06/2021.
  */
class SingletonServiceSpec
    extends SingletonService[SampleCommand, SampleCommandResult]
    with SamplePattern
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  lazy val systemName: String = generateUUID()

  private[this] lazy val testKit = ActorTestKit(systemName)

  override protected def beforeAll(): Unit = {
    init(testKit)
  }

  implicit lazy val system: ActorSystem[Nothing] = testKit.system

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  "SingletonService" must {
    "run commands" in {
      run(TestSample) complete () match {
        case Success(s) =>
          s match {
            case SampleTested => log.info("sample tested !")
            case other        => fail(other.toString)
          }
        case Failure(f) => fail(f.getMessage)
      }
    }
  }
}
