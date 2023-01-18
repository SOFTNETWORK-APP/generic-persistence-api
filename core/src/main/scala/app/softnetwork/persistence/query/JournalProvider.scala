package app.softnetwork.persistence.query

import akka.{Done, NotUsed}

import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.query.scaladsl.ReadJournal

import akka.stream.scaladsl.Source

import com.typesafe.scalalogging.Logger

import app.softnetwork.persistence._

import akka.{actor => classic}

import scala.concurrent.Future

/** Created by smanciot on 07/05/2021.
  */
trait JournalProvider extends ReadJournal with EventStream { _: SchemaProvider =>

  implicit def classicSystem: classic.ActorSystem

  protected final lazy val platformEventProcessorId: String = s"$eventProcessorId-$environment"

  protected final lazy val platformTag: String = s"$tag-$environment"

  protected def logger: Logger

  protected def startOffset(): Offset = Offset.sequence(0L)

  protected def initJournalProvider(): Unit = {}

  /** @param tag
    *   - tag
    * @param offset
    *   - offset
    * @return
    */
  protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed]

  /** Read current offset
    *
    * @return
    */
  protected def readOffset(): Future[Offset] = Future.successful(Offset.sequence(0L))

  /** Persist current offset
    *
    * @param offset
    *   - current offset
    * @return
    */
  protected def writeOffset(offset: Offset): Future[Done] = Future.successful(Done)

  protected def currentPersistenceIds(): Source[String, NotUsed]
}
