package app.softnetwork.notification.handlers

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.ActorSystem
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.notification.config.Settings
import app.softnetwork.notification.message._
import org.softnetwork.notification.model.{From, Mail}
import app.softnetwork.notification.scalatest.NotificationTestKit

/**
  * Created by smanciot on 14/04/2020.
  */
class NotificationHandlerSpec extends MockNotificationHandler with AnyWordSpecLike with NotificationTestKit {

  lazy val from: String = Settings.NotificationConfig.mail.username
  val to = Seq("nobody@gmail.com")
  val subject = "Sujet"
  val message = "message"

  private[this] def _mail(uuid: String): Mail =
    Mail.defaultInstance.withUuid(uuid).withFrom(From(from, None)).withTo(to).withSubject(subject).withMessage(message)

  implicit lazy val system: ActorSystem[Nothing] = typedSystem()

  val probe: TestProbe[Schedule4NotificationTriggered.type] = createTestProbe[Schedule4NotificationTriggered.type]()
  system.eventStream.tell(Subscribe(probe.ref))

  "NotificationTypedHandler" must {

    "add notification" in {
      val uuid = "add"
      this ? (uuid, AddNotification(_mail(uuid))) await {
        case n: NotificationAdded => n.uuid shouldBe uuid
        case _ => fail()
      }
    }

    "remove notification" in {
      val uuid = "remove"
      this ? (uuid, AddNotification(_mail(uuid))) await {
        case n: NotificationAdded =>
          n.uuid shouldBe uuid
          this ? (uuid, RemoveNotification(uuid)) await {
            case _: NotificationRemoved.type => succeed
            case _ => fail()
          }
        case _ => fail()
      }
    }

    "send notification" in {
      val uuid = "send"
      this ? (uuid, SendNotification(_mail(uuid))) await {
        case n: NotificationSent => n.uuid shouldBe uuid
        case _ => fail()
      }
    }

    "resend notification" in {
      val uuid = "resend"
      this ? (uuid, SendNotification(_mail(uuid))) await {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (uuid, ResendNotification(uuid)) await {
            case n: NotificationSent => n.uuid shouldBe uuid
            case _ => fail()
          }
          this ? ("fake", ResendNotification(uuid)) await {
            case NotificationNotFound => succeed
            case _ => fail()
          }
        case _ => fail()
      }
    }

    "retrieve notification status" in {
      val uuid = "status"
      this ? (uuid, SendNotification(_mail(uuid))) await {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (uuid, GetNotificationStatus(uuid)) await {
            case n: NotificationSent => n.uuid shouldBe uuid
            case _ => fail()
          }
        case _ => fail()
      }
    }

    "trigger notification" in {
      val uuid = "trigger"
      this ? (uuid, SendNotification(_mail(uuid))) await {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (uuid, GetNotificationStatus(uuid)) await {
            case n: NotificationSent =>
              n.uuid shouldBe uuid
              succeed
            case _ =>
              probe.expectMessage(Schedule4NotificationTriggered)
              succeed
          }
        case _ => fail()
      }
    }
  }
}
