package app.softnetwork.persistence.service

import app.softnetwork.persistence.message.{Command, CommandResult}
import app.softnetwork.persistence.typed.scaladsl.SingletonPattern

import scala.concurrent.Future
import scala.reflect.ClassTag

/** Created by smanciot on 15/04/2020.
  */
trait SingletonService[C <: Command, R <: CommandResult] extends Service[C, R] {
  _: SingletonPattern[C, R] =>

  def run(command: C)(implicit tTag: ClassTag[C]): Future[R] = {
    this ? command
  }

}
