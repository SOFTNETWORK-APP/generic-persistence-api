package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.jdbc.query.JdbcJournalProvider
import app.softnetwork.persistence.query.OffsetProvider

trait PersonToInMemoryProcessorStreamWithJdbcJournal
    extends PersonToExternalProcessorStream
    with JdbcJournalProvider
    with InMemoryPersonPersistenceProvider { _: OffsetProvider => }
