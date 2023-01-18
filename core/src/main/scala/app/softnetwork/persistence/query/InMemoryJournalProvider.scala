package app.softnetwork.persistence.query

import akka.{Done, NotUsed}
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery, Sequence}
import akka.stream.scaladsl.Source

import scala.concurrent.Future

/** Created by smanciot on 12/05/2021.
  */
trait InMemoryJournalProvider extends JournalProvider with InMemorySchemaProvider {

  private[this] lazy val readJournal: InMemoryReadJournal =
    PersistenceQuery(classicSystem).readJournalFor[InMemoryReadJournal](
      InMemoryReadJournal.Identifier
    )

  override protected def initJournalProvider(): Unit = {}

  private[this] var _offset: Long = 0L

  /** Read current offset
    *
    * @return
    */
  override protected def readOffset(): Future[Offset] = Future.successful(Offset.sequence(_offset))

  /** Persist current offset
    *
    * @param offset
    *   - current offset
    * @return
    */
  override protected def writeOffset(offset: Offset): Future[Done] = {
    offset match {
      case Sequence(value) => _offset = value
      case _               =>
    }
    Future.successful(Done)
  }

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
