package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.jdbc.query.{
  JdbcJournalProvider,
  JdbcOffsetProvider,
  JdbcStateProvider,
  State2JdbcProcessorStream
}
import app.softnetwork.persistence.person.message.PersonEvent
import app.softnetwork.persistence.person.model.Person
import slick.jdbc.JdbcProfile

trait PersonToJdbcProcessorStreamWithJdbcJournal
    extends State2JdbcProcessorStream[Person, PersonEvent]
    with PersonToExternalProcessorStream
    with JdbcJournalProvider
    with JdbcOffsetProvider
    with JdbcStateProvider[Person] { _: JdbcProfile => }
