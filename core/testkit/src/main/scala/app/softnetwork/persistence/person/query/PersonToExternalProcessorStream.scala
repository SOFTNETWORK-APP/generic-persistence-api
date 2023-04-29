package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.person.message.PersonEvent
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.query.{
  ExternalPersistenceProvider,
  JournalProvider,
  OffsetProvider,
  State2ExternalProcessorStream
}

trait PersonToExternalProcessorStream extends State2ExternalProcessorStream[Person, PersonEvent] {
  _: JournalProvider with OffsetProvider with ExternalPersistenceProvider[Person] =>
  override final val externalProcessor: String = "external"
}
