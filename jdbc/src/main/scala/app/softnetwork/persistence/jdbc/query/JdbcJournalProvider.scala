package app.softnetwork.persistence.jdbc.query

import akka.NotUsed

import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}

import akka.stream.scaladsl.Source

import app.softnetwork.persistence.query.JournalProvider

/** Created by smanciot on 16/05/2020.
  */
trait JdbcJournalProvider extends JournalProvider {

  private[this] lazy val readJournal =
    PersistenceQuery(classicSystem).readJournalFor[JdbcReadJournal](
      JdbcReadJournal.Identifier
    )

  /** @param tag
    *   - tag
    * @param offset
    *   - offset
    * @return
    */
  override protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    readJournal.eventsByTag(tag, offset)

  override protected def currentPersistenceIds(): Source[String, NotUsed] = {
    readJournal.currentPersistenceIds()
  }
}
