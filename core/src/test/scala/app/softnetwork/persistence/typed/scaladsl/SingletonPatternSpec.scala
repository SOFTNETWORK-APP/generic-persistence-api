package app.softnetwork.persistence.typed.scaladsl

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.receptionist.ServiceKey
import app.softnetwork.persistence._
import app.softnetwork.persistence.message.{Command, CommandResult, CommandWrapper}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.implicitConversions
import scala.util.{Failure, Success}

/** Created by smanciot on 02/06/2021.
  */
class SingletonPatternSpec extends SamplePattern with AnyWordSpecLike with BeforeAndAfterAll {

  lazy val systemName: String = generateUUID()

  private[this] lazy val testKit = ActorTestKit(systemName)

  override protected def beforeAll(): Unit = {
    init(testKit)
  }

  implicit lazy val system: ActorSystem[Nothing] = testKit.system

  def test(): Unit = this ? TestSample complete () match {
    case Success(s) =>
      s match {
        case SampleTested => logger.info("sample tested !")
        case other        => fail(other.toString)
      }
    case Failure(f) => fail(f.getMessage)
  }

  def test2(): Unit = this ! TestSample

  "SingletonPattern" must {
    "handle commands" in {
      test()
      test()
      test2()
    }
  }
}

trait SamplePattern extends SingletonPattern[SampleCommand, SampleCommandResult] {

  implicit def command2Request(command: SampleCommand): Request = replyTo =>
    SampleCommandWrapper(command, replyTo)

  override lazy val name = "Sample"

  override lazy val key: ServiceKey[SampleCommand] = ServiceKey[SampleCommand](name)

  override def handleCommand(
    command: SampleCommand,
    replyTo: Option[ActorRef[SampleCommandResult]]
  )(implicit context: ActorContext[SampleCommand]): Unit = {
    command match {
      case TestSample => replyTo.foreach(_ ! SampleTested)
      case _          =>
    }
  }
}

sealed trait SampleCommand extends Command

case class SampleCommandWrapper(command: SampleCommand, replyTo: ActorRef[SampleCommandResult])
    extends CommandWrapper[SampleCommand, SampleCommandResult]
    with SampleCommand

case object TestSample extends SampleCommand

sealed trait SampleCommandResult extends CommandResult

case object SampleTested extends SampleCommandResult
