package app.softnetwork.resource.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.InMemoryJournalProvider
import app.softnetwork.resource.message.ResourceEvents.ResourceEvent
import app.softnetwork.resource.message.ResourceMessages.{ResourceCommand, ResourceResult}
import app.softnetwork.resource.model.Resource
import app.softnetwork.resource.persistence.query.{GenericResourceToExternalProcessorStream, ResourceToLocalFileSystemProcessorStream}
import app.softnetwork.resource.persistence.typed.ResourceBehavior
import org.scalatest.Suite

trait ResourceToLocalFileSystemTestKit extends GenericResourceTestKit[Resource] {_: Suite =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  override def resourceEntity: ActorSystem[_] =>
    PersistentEntity[ResourceCommand, Resource, ResourceEvent, ResourceResult] = _ => ResourceBehavior

  override def resourceToExternalProcessorStream: ActorSystem[_] => GenericResourceToExternalProcessorStream[Resource] = sys =>
    new ResourceToLocalFileSystemProcessorStream with InMemoryJournalProvider {
      override val forTests = true

      override implicit def system: ActorSystem[_] = sys
    }

}