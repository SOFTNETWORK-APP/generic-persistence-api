package app.softnetwork.resource.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.launch.{PersistenceGuardian, PersistentEntity}
import app.softnetwork.persistence.query.{EventProcessorStream, SchemaProvider}
import app.softnetwork.persistence.typed.Singleton
import app.softnetwork.resource.persistence.query.ResourceToExternalProcessorStream
import app.softnetwork.resource.persistence.typed.ResourceBehavior
import com.typesafe.scalalogging.StrictLogging

trait ResourceGuardian extends PersistenceGuardian with StrictLogging {_: SchemaProvider =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  def resourceEntities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = _ => Seq(
    ResourceBehavior
  )

  /**
    * initialize all entities
    *
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = resourceEntities

  /**
    *
    * initialize all singletons
    */
  override def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq.empty

  def resourceToExternalProcessorStream:  ActorSystem[_] => ResourceToExternalProcessorStream

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    Seq(
      resourceToExternalProcessorStream(sys)
    )

}
