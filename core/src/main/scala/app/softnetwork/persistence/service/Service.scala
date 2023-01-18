package app.softnetwork.persistence.service

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.typed.scaladsl.Patterns
import app.softnetwork.persistence.message.{Command, CommandResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Created by smanciot on 15/04/2020.
  */
trait Service[C <: Command, R <: CommandResult] { _: Patterns[C, R] =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContext = system.executionContext

  def run(entityId: String, command: C)(implicit tTag: ClassTag[C]): Future[R] = {
    this ?? (entityId, command)
  }

}
