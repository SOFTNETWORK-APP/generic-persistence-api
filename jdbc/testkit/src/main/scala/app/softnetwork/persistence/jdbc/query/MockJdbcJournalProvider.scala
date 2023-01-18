package app.softnetwork.persistence.jdbc.query

import akka.Done
import akka.persistence.query.{Offset, Sequence}

import scala.concurrent.Future

trait MockJdbcJournalProvider extends JdbcJournalProvider { _: JdbcSchemaProvider =>
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
}
