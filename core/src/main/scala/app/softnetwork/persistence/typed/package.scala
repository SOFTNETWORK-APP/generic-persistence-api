package app.softnetwork.persistence

import akka.actor.typed.ActorSystem

import scala.language.implicitConversions

/**
  * Created by smanciot on 16/05/2020.
  */
package object typed {

  import akka.{actor => classic}

  implicit def typed2classic(system: ActorSystem[_]): classic.ActorSystem = {
    import akka.actor.typed.scaladsl.adapter._
    system.toClassic
  }

}
