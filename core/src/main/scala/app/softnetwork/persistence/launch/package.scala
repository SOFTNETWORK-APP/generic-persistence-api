package app.softnetwork.persistence

import akka.actor.typed.ActorSystem
import akka.cluster.ClusterEvent.ClusterDomainEvent
import app.softnetwork.persistence.message.{Command, CommandResult, Event}
import app.softnetwork.persistence.model.State
import app.softnetwork.persistence.typed.EntityBehavior

package object launch {

  trait ClusterDomainEventHandler {
    def handleEvent(event: ClusterDomainEvent)(implicit system: ActorSystem[_]): Unit = ()
  }

  case class PersistentEntity[C <: Command, S <: State, E <: Event, R <: CommandResult](
    entity: EntityBehavior[C, S, E, R],
    role: Option[String] = None
  )
}
