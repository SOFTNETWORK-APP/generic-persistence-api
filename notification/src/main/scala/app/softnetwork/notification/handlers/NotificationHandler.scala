package app.softnetwork.notification.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.notification.message._
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.typed._

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * Created by smanciot on 14/04/2020.
  */

trait AllNotificationsTypeKey extends CommandTypeKey[NotificationCommand]{
  override def TypeKey(implicit tTag: ClassTag[NotificationCommand]): EntityTypeKey[NotificationCommand] =
    AllNotificationsBehavior.TypeKey
}

trait MockAllNotificationsTypeKey extends CommandTypeKey[NotificationCommand]{
  override def TypeKey(implicit tTag: ClassTag[NotificationCommand]): EntityTypeKey[NotificationCommand] =
    MockAllNotificationsBehavior.TypeKey
}

trait NotificationHandler extends EntityPattern[NotificationCommand, NotificationCommandResult]
  with AllNotificationsTypeKey

trait MockNotificationHandler extends NotificationHandler with MockAllNotificationsTypeKey

trait NotificationDao {_: NotificationHandler =>

  def sendNotification(notification: Notification)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec = system.executionContext
    this !? new SendNotification(notification) map {
      case _: NotificationSent      => true
      case _: NotificationDelivered => true
      case _                        => false
    }
  }

  def resendNotification(id: String)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec = system.executionContext
    this !? new ResendNotification(id) map {
      case _: NotificationSent      => true
      case _: NotificationDelivered => true
      case _                        => false
    }
  }

  def removeNotification(id: String)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec = system.executionContext
    this !? new RemoveNotification(id) map {
      case NotificationRemoved => true
      case _                   => false
    }
  }

  def geNotificationStatus(id: String)(implicit system: ActorSystem[_]) = {
    implicit val ec = system.executionContext
    this !? new GetNotificationStatus(id)
  }

}

trait MockNotificationDao extends NotificationDao with MockNotificationHandler

object NotificationDao extends NotificationDao with NotificationHandler

object MockNotificationDao extends MockNotificationDao
