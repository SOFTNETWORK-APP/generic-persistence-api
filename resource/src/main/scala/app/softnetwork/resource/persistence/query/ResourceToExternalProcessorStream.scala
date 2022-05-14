package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.query.{ExternalPersistenceProvider, JournalProvider, State2ExternalProcessorStream}
import app.softnetwork.resource.message.ResourceEvents.ResourceEvent
import app.softnetwork.resource.model.Resource

trait ResourceToExternalProcessorStream extends State2ExternalProcessorStream[Resource, ResourceEvent]
  with ManifestWrapper[Resource] {_: JournalProvider with ExternalPersistenceProvider[Resource] =>

  override val externalProcessor = "resource"

  override protected val manifestWrapper: ManifestW = ManifestW()

}

trait ResourceToLocalFileSystemProcessorStream extends ResourceToExternalProcessorStream
  with LocalFileSystemResourceProvider{_: JournalProvider =>
  override val externalProcessor: String = "localfilesystem"
}