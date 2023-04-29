package app.softnetwork.persistence.query

import akka.NotUsed
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.scaladsl.Source

/** Created by smanciot on 12/05/2021.
  */
trait InMemoryJournalProvider extends JournalProvider {

  private[this] lazy val readJournal: InMemoryReadJournal =
    PersistenceQuery(classicSystem).readJournalFor[InMemoryReadJournal](
      InMemoryReadJournal.Identifier
    )

  override protected def currentPersistenceIds(): Source[String, NotUsed] =
    readJournal.currentPersistenceIds()

  /** @param tag
    *   - tag
    * @param offset
    *   - offset
    * @return
    */
  override protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    readJournal.eventsByTag(tag, offset)
}
