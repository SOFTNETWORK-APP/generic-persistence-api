package app.softnetwork.notification.peristence.query

import akka.actor.typed.eventstream.EventStream.Publish
import org.softnetwork.akka.model.Schedule
import app.softnetwork.persistence.query.JournalProvider
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.message._

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
      case _ => false
    }
  }
}
