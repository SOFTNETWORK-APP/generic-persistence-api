package app.softnetwork.persistence.typed.scaladsl

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.ActorContext
import app.softnetwork.persistence.message.SampleMessages._
import org.slf4j.Logger

import scala.language.implicitConversions

trait SamplePattern extends SingletonPattern[SampleCommand, SampleCommandResult] {
  _: { def log: Logger } =>

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
