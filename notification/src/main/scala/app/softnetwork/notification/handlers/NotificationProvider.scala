package app.softnetwork.notification.handlers

import java.util.{Date, UUID}

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import app.softnetwork.notification.model.Notification

import org.softnetwork.notification.model._

/**
  * Created by smanciot on 14/04/2018.
  */
trait NotificationProvider[T<:Notification] {
  def send(notification: T)(implicit system: ActorSystem[_]): NotificationAck
  def ack(notification: T)(implicit system: ActorSystem[_]): NotificationAck = NotificationAck(notification.ackUuid, notification.results, new Date())
}

trait MockNotificationProvider[T<:Notification] extends NotificationProvider[T] with StrictLogging {

  override def send(notification: T)(implicit system: ActorSystem[_]): NotificationAck = {
    notification match {
      case m: Mail => logger.info(s"\r\n${m.richMessage}")
      case _ => logger.info(s"\r\n${notification.message}")
    }
    NotificationAck(
      Some(UUID.randomUUID().toString),
      notification.to.map(recipient => NotificationStatusResult(recipient, NotificationStatus.Sent, None)),
      new Date()
    )
  }

}

trait AllNotificationsProvider extends NotificationProvider[Notification] {
  override def send(notification: Notification)(implicit system: ActorSystem[_]): NotificationAck =
    notification match {
      case mail: Mail => MailProvider.send(mail)
      case push: Push => PushProvider.send(push)
      case sms: SMS => SMSModeProvider.send(sms)
      case _ => NotificationAck(None, Seq.empty, new Date())
    }

  override def ack(notification: Notification)(implicit system: ActorSystem[_]): NotificationAck =
    notification match {
      case mail: Mail => MailProvider.ack(mail)
      case push: Push => PushProvider.ack(push)
      case sms: SMS => SMSModeProvider.ack(sms)
      case _ => NotificationAck(notification.ackUuid, notification.results, new Date())
    }
}

trait MockAllNotificationsProvider extends NotificationProvider[Notification] with StrictLogging {
  override def send(notification: Notification)(implicit system: ActorSystem[_]): NotificationAck = {
    notification match {
      case mail: Mail => MockMailProvider.send(mail)
      case push: Push => MockPushProvider.send(push)
      case sms: SMS => MockSMSProvider.send(sms)
      case _ => NotificationAck(None, Seq.empty, new Date())
    }
  }
}
