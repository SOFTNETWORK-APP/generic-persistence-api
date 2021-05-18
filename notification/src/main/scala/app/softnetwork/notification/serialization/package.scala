package app.softnetwork.notification

import app.softnetwork.protobuf.ScalaPBSerializers
import ScalaPBSerializers.GeneratedEnumSerializer

import app.softnetwork.serialization._
import org.softnetwork.notification.model.{MailType, Platform, NotificationStatus, NotificationType}

import scala.language.implicitConversions

/**
  * Created by smanciot on 21/05/2020.
  */
package object serialization {

  val notificationFormats =
    commonFormats ++
      Seq(
        GeneratedEnumSerializer(NotificationType.enumCompanion),
        GeneratedEnumSerializer(NotificationStatus.enumCompanion),
        GeneratedEnumSerializer(Platform.enumCompanion),
        GeneratedEnumSerializer(MailType.enumCompanion)
      )

  implicit def formats = notificationFormats

  implicit def toNotificationType(channel: String): Option[NotificationType] = NotificationType.fromName(channel)

  implicit def toChannels(channels: List[String]): List[NotificationType] = channels.flatMap(toNotificationType)
}
