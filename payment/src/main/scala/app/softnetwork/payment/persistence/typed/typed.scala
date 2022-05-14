package app.softnetwork.payment.persistence

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.persistence.typed._

import scala.language.implicitConversions

/**
  * Created by smanciot on 27/04/2020.
  */
package object typed {

  trait PaymentBehavior[C  <: Command, S  <: Timestamped, E  <: Event, R  <: CommandResult]
    extends TimeStampedBehavior[C, S, E, R] with ManifestWrapper[S] {

    /**
      *
      * Set event tags, which will be used in persistence query
      *
      * @param entityId - entity id
      * @param event    - the event to tag
      * @return event tags
      */
    override protected def tagEvent(entityId: String, event: E): Set[String] = {
      event match {
        case _: CrudEvent => Set(s"${persistenceId.toLowerCase}-to-elastic")
        case _ => Set(persistenceId)
      }
    }
  }
}
