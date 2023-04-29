package app.softnetwork.persistence.typed.scaladsl

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence._
import app.softnetwork.persistence.message.SampleMessages.{SampleTested, TestSample}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.{Logger, LoggerFactory}

import scala.language.implicitConversions
import scala.util.{Failure, Success}

/** Created by smanciot on 02/06/2021.
  */
class SingletonPatternSpec extends SamplePattern with AnyWordSpecLike with BeforeAndAfterAll {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  lazy val systemName: String = generateUUID()

  private[this] lazy val testKit = ActorTestKit(systemName)

  override protected def beforeAll(): Unit = {
    init(testKit)
  }

  implicit lazy val system: ActorSystem[Nothing] = testKit.system

  def ask(): Unit = this ? TestSample complete () match {
    case Success(s) =>
      s match {
        case SampleTested => log.info("sample tested !")
        case other        => fail(other.toString)
      }
    case Failure(f) => fail(f.getMessage)
  }

  def tell(): Unit = this ! TestSample

  "SingletonPattern" must {
    "handle commands" in {
      ask()
      ask()
      tell()
    }
  }
}
