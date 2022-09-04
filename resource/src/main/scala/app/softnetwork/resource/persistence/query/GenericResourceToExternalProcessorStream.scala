package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.query.{ExternalPersistenceProvider, JournalProvider, State2ExternalProcessorStream}
import app.softnetwork.resource.message.ResourceEvents.ResourceEvent
import app.softnetwork.resource.model.GenericResource

trait GenericResourceToExternalProcessorStream[Resource <: GenericResource] extends State2ExternalProcessorStream[Resource, ResourceEvent]
  with ManifestWrapper[Resource] {_: JournalProvider with ExternalPersistenceProvider[Resource] =>

  override val externalProcessor = "resource"
}
