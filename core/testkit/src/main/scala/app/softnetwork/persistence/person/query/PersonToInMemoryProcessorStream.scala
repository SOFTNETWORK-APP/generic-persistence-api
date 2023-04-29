package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.query.{InMemoryJournalProvider, InMemoryOffsetProvider}

trait PersonToInMemoryProcessorStream
    extends PersonToExternalProcessorStream
    with InMemoryJournalProvider
    with InMemoryOffsetProvider
    with InMemoryPersonPersistenceProvider
