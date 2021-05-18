package app.softnetwork.notification.peristence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import org.softnetwork.akka.model.Schedule
import app.softnetwork.persistence.query.{EventProcessorStream, JournalProvider}
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.message._
import org.softnetwork.notification.message._

import scala.concurrent.Future

/**
  * Created by smanciot on 04/09/2020.
  */
trait Scheduler2NotificationProcessorStream
  extends Scheduler2EntityProcessorStream[NotificationCommand, NotificationCommandResult] {
  _: JournalProvider with NotificationHandler =>

  protected val forTests = false

  override protected def triggerSchedule(schedule: Schedule): Future[Boolean] = {
    !? (TriggerSchedule4Notification(schedule)) map {
      case Schedule4NotificationTriggered =>
        if(forTests){
          system.eventStream.tell(Publish(Schedule4NotificationTriggered))
        }
        true
      case other => false
    }
  }
}

trait Entity2NotificationProcessorStream extends EventProcessorStream[NotificationEventWithCommand] {
  _: JournalProvider with EntityPattern[NotificationCommand, NotificationCommandResult] =>

  protected val forTests = false

  /**
    *
    * Processing event
    *
    * @param event         - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr    - sequence number
    * @return
    */
  override protected def processEvent(event: NotificationEventWithCommand, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    this !? event.command map {
      case r: NotificationErrorMessage => throw new Throwable(r.message)
      case result =>
        if(forTests){
          system.eventStream.tell(Publish(result))
        }
        Done
    }
  }
}