package app.softnetwork.resource.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.query.InMemoryJournalProvider
import app.softnetwork.resource.model.Resource
import app.softnetwork.resource.persistence.query.{GenericResourceToExternalProcessorStream, ResourceToLocalFileSystemProcessorStream}
import org.scalatest.Suite

trait ResourceToLocalFileSystemTestKit extends GenericResourceTestKit[Resource] {_: Suite =>

  override def resourceToExternalProcessorStream: ActorSystem[_] => GenericResourceToExternalProcessorStream[Resource] = sys =>
    new ResourceToLocalFileSystemProcessorStream with InMemoryJournalProvider {
      override val forTests = true

      override implicit def system: ActorSystem[_] = sys
    }

}