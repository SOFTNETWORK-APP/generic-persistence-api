package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.query.JournalProvider
import app.softnetwork.resource.model.Resource

trait ResourceToLocalFileSystemProcessorStream extends GenericResourceToExternalProcessorStream[Resource]
  with LocalFileSystemResourceProvider{_: JournalProvider =>
  override val externalProcessor: String = "localfilesystem"
  override protected val manifestWrapper: ManifestW = ManifestW()
}