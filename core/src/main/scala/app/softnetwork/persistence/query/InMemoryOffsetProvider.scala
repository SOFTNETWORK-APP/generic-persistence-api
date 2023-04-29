package app.softnetwork.persistence.query

import akka.Done
import akka.persistence.query.{Offset, Sequence}

import scala.concurrent.Future

trait InMemoryOffsetProvider extends OffsetProvider {

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
