package app.softnetwork.persistence

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props}
import app.softnetwork.persistence.message.{Command, CommandResult}

import scala.language.{implicitConversions, reflectiveCalls}

/** Created by smanciot on 16/05/2020.
  */
package object typed {

  import akka.{actor => classic}

  implicit def typed2classic(system: ActorSystem[_]): classic.ActorSystem = {
    import akka.actor.typed.scaladsl.adapter._
    system.toClassic
  }

  trait Singleton[C <: Command] {

    type Context = {
      def spawn[T](behavior: Behavior[T], name: String, props: Props): ActorRef[T]
      def system: ActorSystem[_]
    }

    def behavior: Behavior[C]

    def key: ServiceKey[C]

    /** a unique name for the service
      */
    def name: String

    protected var singletonRef: ActorRef[C] = _

    def init(implicit context: Context): Unit = {
      singletonRef = context.spawn(behavior, name, Props.empty)
      context.system.receptionist ! Receptionist.Register(key, singletonRef)
    }
  }

  sealed trait MaybeReply[R <: CommandResult] {
    def apply(): Option[ActorRef[R]] => Unit
    final def ~>(replyTo: Option[ActorRef[R]]): Unit = apply()(replyTo)
  }

  implicit def resultToMaybeReply[R <: CommandResult](r: R): MaybeReply[R] = new MaybeReply[R] {
    def apply(): Option[ActorRef[R]] => Unit = {
      case Some(subscriber) => subscriber ! r
      case _                =>
    }
  }
}
