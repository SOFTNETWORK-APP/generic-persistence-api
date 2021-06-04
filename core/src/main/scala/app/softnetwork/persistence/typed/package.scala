package app.softnetwork.persistence

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{Props, ActorRef, Behavior, ActorSystem}
import app.softnetwork.persistence.message.Command

import scala.language.{reflectiveCalls, implicitConversions}

/**
  * Created by smanciot on 16/05/2020.
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

    /**
      *
      * a unique name for the service
      */
    def name: String

    protected var singletonRef: ActorRef[C] = _

    def init(implicit context: Context): Unit = {
      singletonRef = context.spawn(behavior, name, Props.empty)
      context.system.receptionist ! Receptionist.Register(key, singletonRef)
    }
  }

}
