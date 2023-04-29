package app.softnetwork.persistence.query

import akka.NotUsed
import akka.{actor => classic}

import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.query.scaladsl.ReadJournal

import akka.stream.scaladsl.Source

/** Created by smanciot on 07/05/2021.
  */
trait JournalProvider extends ReadJournal {

  implicit def classicSystem: classic.ActorSystem

  /** @param tag
    *   - tag
    * @param offset
    *   - offset
    * @return
    */
  protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed]

  protected def currentPersistenceIds(): Source[String, NotUsed]
}
