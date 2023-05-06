package app.softnetwork.persistence.query

import akka.Done
import akka.{actor => classic}
import akka.persistence.query.Offset
import app.softnetwork.persistence.environment

import scala.concurrent.Future

trait OffsetProvider { _: EventStream =>

  implicit def classicSystem: classic.ActorSystem

  protected final lazy val platformEventProcessorId: String = s"$eventProcessorId-$environment"

  protected final lazy val platformTag: String = s"$tag-$environment"

  protected def initOffset(): Unit = ()

  /** Read current offset
    *
    * @return
    */
  protected def readOffset(): Future[Offset]

  /** Persist current offset
    *
    * @param offset
    *   - current offset
    * @return
    */
  protected def writeOffset(offset: Offset): Future[Done]

}
