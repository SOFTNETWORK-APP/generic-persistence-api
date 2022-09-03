package app.softnetwork.notification.peristence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.notification.config.Settings
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.message.{AddNotification, NotificationAdded, NotificationRemoved, RemoveNotification}
import app.softnetwork.persistence.query.{EventProcessorStream, JournalProvider}
import org.softnetwork.notification.message.{AddNotificationCommandEvent, NotificationCommandEvent, RemoveNotificationCommandEvent, WrapNotificationCommandEvent}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait NotificationCommandProcessorStream extends EventProcessorStream[NotificationCommandEvent] {
  _: JournalProvider with NotificationHandler =>

  override lazy val tag: String = Settings.NotificationConfig.eventStreams.externalToNotificationTag

  /**
    *
    * @return whether or not the events processed by this processor stream would be published to the main bus event
    */
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
  override protected def processEvent(event: NotificationCommandEvent, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    event match {
      case evt: WrapNotificationCommandEvent =>
        val promise = Promise[Done]
        processEvent(evt.event, persistenceId, sequenceNr) onComplete {
          case Success(_) => promise.success(Done)
          case Failure(f) =>
            logger.error(f.getMessage)
            promise.failure(f)
        }
        promise.future
      case evt: AddNotificationCommandEvent =>
        val command = AddNotification(evt.notification)
        !? (command) map {
          case _: NotificationAdded =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case evt: RemoveNotificationCommandEvent =>
        val command = RemoveNotification(evt.uuid)
        !? (command) map {
          case NotificationRemoved =>
            if(forTests) system.eventStream.tell(Publish(event))
            Done
          case other =>
            logger.error(s"$platformEventProcessorId - command $command returns unexpectedly ${other.getClass}")
            Done
        }
      case other =>
        logger.warn(s"$platformEventProcessorId does not support event [${other.getClass}]")
        Future.successful(Done)
    }
  }
}
