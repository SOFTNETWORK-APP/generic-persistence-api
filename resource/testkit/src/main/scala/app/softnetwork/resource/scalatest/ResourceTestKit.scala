package app.softnetwork.resource.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.query.InMemoryJournalProvider
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.resource.config.Settings
import app.softnetwork.resource.launch.ResourceGuardian
import app.softnetwork.resource.persistence.query.{ResourceToExternalProcessorStream, ResourceToLocalFileSystemProcessorStream}
import org.scalatest.Suite

trait ResourceTestKit extends ResourceGuardian with InMemoryPersistenceTestKit {_: Suite =>

  /**
    *
    * @return roles associated with this node
    */
  override def roles: Seq[String] = Seq(Settings.AkkaNodeRole)

}

trait ResourceToLocalFileSystemTestKit extends ResourceTestKit {_: Suite =>

  override def resourceToExternalProcessorStream: ActorSystem[_] => ResourceToExternalProcessorStream = sys =>
    new ResourceToLocalFileSystemProcessorStream with InMemoryJournalProvider {
      override val forTests = true

      override implicit def system: ActorSystem[_] = sys
    }

}