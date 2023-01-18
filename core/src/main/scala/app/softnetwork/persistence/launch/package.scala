package app.softnetwork.persistence

import akka.actor.typed.ActorSystem
import akka.cluster.ClusterEvent.{ClusterDomainEvent, ClusterShuttingDown}
import app.softnetwork.persistence.message.{Command, CommandResult, Event}
import app.softnetwork.persistence.model.State
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.persistence.typed.EntityBehavior

package object launch {

  trait ClusterDomainEventHandler { _: SchemaProvider =>
    def handleEvent(event: ClusterDomainEvent)(implicit system: ActorSystem[_]): Unit = {
      event match {
        case ClusterShuttingDown => shutdown()
        case _                   =>
      }
    }
  }

  case class PersistentEntity[C <: Command, S <: State, E <: Event, R <: CommandResult](
    entity: EntityBehavior[C, S, E, R],
    role: Option[String] = None
  )
}
