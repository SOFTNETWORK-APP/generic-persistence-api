package app.softnetwork.notification

import app.softnetwork.persistence.message._
import org.softnetwork.akka.model.Schedule
import app.softnetwork.notification.model.Notification

/**
  * Created by smanciot on 15/04/2020.
  */
package object message {

  sealed trait NotificationCommand extends EntityCommand

  @SerialVersionUID(0L)
  case object ScheduleNotification extends NotificationCommand with AllEntities

  @SerialVersionUID(0L)
  case class AddNotification[T<:Notification](notification: T) extends NotificationCommand {
    override val id: String = notification.uuid
  }

  @SerialVersionUID(0L)
  case class RemoveNotification(id: String) extends NotificationCommand

  @SerialVersionUID(0L)
  case class SendNotification[T<:Notification](notification: T) extends NotificationCommand {
    override val id: String = notification.uuid
  }

  @SerialVersionUID(0L)
  case class ResendNotification(id: String) extends NotificationCommand

  @SerialVersionUID(0L)
  case class GetNotificationStatus(id: String) extends NotificationCommand

  case class TriggerSchedule4Notification(schedule: Schedule) extends NotificationCommand {
    override val id: String = schedule.entityId
  }

  sealed trait NotificationCommandResult extends CommandResult

  @SerialVersionUID(0L)
  case class NotificationAdded(uuid: String) extends NotificationCommandResult

  case object NotificationRemoved extends NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationSent(uuid: String) extends NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationDelivered(uuid: String) extends NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationPending(uuid: String) extends NotificationCommandResult

  case class Schedule4NotificationTriggered(schedule: Schedule) extends NotificationCommandResult

  @SerialVersionUID(0L)
  class NotificationErrorMessage (override val message: String) extends ErrorMessage(message) with NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationUndelivered(uuid: String) extends NotificationErrorMessage("NotificationNotDelivered")

  @SerialVersionUID(0L)
  case class NotificationRejected(uuid: String) extends NotificationErrorMessage("NotificationRejected")

  case object NotificationNotFound extends NotificationErrorMessage("NotificationNotFound")

  case object NotificationMaxTriesReached extends NotificationErrorMessage("NotificationMaxTriesReached")

  case object NotificationUnknownCommand extends NotificationErrorMessage("NotificationUnknownCommand")

  case object Schedule4NotificationNotTriggered extends NotificationErrorMessage("Schedule4NotificationNotTriggered")
}
