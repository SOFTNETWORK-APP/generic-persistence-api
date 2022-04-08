package app.softnetwork.persistence.auth.persistence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.persistence.query.{EventProcessorStream, JournalProvider}
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.persistence.auth.handlers.AccountHandler
import app.softnetwork.persistence.auth.message._

import scala.concurrent.Future

/**
  * Created by smanciot on 01/03/2021.
  */
object AccountEventProcessorStreams {

  trait InternalAccountEvents2AccountProcessorStream extends EventProcessorStream[InternalAccountEvent]
    with AccountHandler {_: JournalProvider with CommandTypeKey[AccountCommand] =>

    def forTests: Boolean = false

    /**
      *
      * Processing event
      *
      * @param event         - event to process
      * @param persistenceId - persistence id
      * @param sequenceNr    - sequence number
      * @return
      */
    override protected def processEvent(event: InternalAccountEvent, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
      import event._
      val command = WrapInternalAccountEvent(event)
      ? (uuid, command) map {
        case e: AccountErrorMessage =>
          logger.error(s"$platformEventProcessorId - command ${command.getClass} returns unexpectedly ${e.message}")
          Done
        case r: AccountCommandResult =>
          if(forTests) system.eventStream.tell(Publish(r))
          Done
      }
    }
  }

}
