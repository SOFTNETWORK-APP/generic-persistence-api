package app.softnetwork.notification.handlers

import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.query.{InMemoryJournalProvider, EventProcessorStream}
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream
import app.softnetwork.notification.config.Settings
import app.softnetwork.notification.message._
import org.softnetwork.notification.model.{From, Mail}
import app.softnetwork.notification.peristence.query.Scheduler2NotificationProcessorStream
import app.softnetwork.notification.peristence.typed.MockAllNotificationsBehavior

/**
  * Created by smanciot on 14/04/2020.
  */
class NotificationHandlerSpec extends MockNotificationHandler with AnyWordSpecLike with InMemoryPersistenceTestKit {

  lazy val from = Settings.Config.mail.username
  val to = Seq("nobody@gmail.com")
  val subject = "Sujet"
  val message = "message"

  private[this] def _mail(uuid: String) =
    Mail.defaultInstance.withUuid(uuid).withFrom(From(from, None)).withTo(to).withSubject(subject).withMessage(message)

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: (ActorSystem[_]) => Seq[EntityBehavior[_, _, _, _]] = system => List(
    MockAllNotificationsBehavior,
    SchedulerBehavior
  )

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = asystem => List(
    new Entity2SchedulerProcessorStream() with SchedulerHandler with InMemoryJournalProvider {
      override val tag = s"${MockAllNotificationsBehavior.persistenceId}-to-scheduler"
      override val forTests = true
      override implicit val system: ActorSystem[_] = asystem
    },
    new Scheduler2NotificationProcessorStream() with MockNotificationHandler with InMemoryJournalProvider {
      override val tag = s"${MockAllNotificationsBehavior.persistenceId}-scheduler"
      override val forTests = true
      override implicit val system: ActorSystem[_] = asystem
    }
  )

  implicit lazy val system = typedSystem()

  val probe = createTestProbe[Schedule4NotificationTriggered.type]()
  system.eventStream.tell(Subscribe(probe.ref))

  "NotificationTypedHandler" must {

    "add notification" in {
      val uuid = "add"
      this ? (uuid, new AddNotification(_mail(uuid))) await {
        case n: NotificationAdded => n.uuid shouldBe uuid
        case _                    => fail()
      }
    }

    "remove notification" in {
      val uuid = "remove"
      this ? (uuid, new AddNotification(_mail(uuid))) await {
        case n: NotificationAdded =>
          n.uuid shouldBe uuid
          this ? (uuid, new RemoveNotification(uuid)) await {
            case _: NotificationRemoved.type => succeed
            case _ => fail()
          }
        case _ => fail()
      }
    }

    "send notification" in {
      val uuid = "send"
      this ? (uuid, new SendNotification(_mail(uuid))) await {
        case n: NotificationSent => n.uuid shouldBe uuid
        case _                   => fail()
      }
    }

    "resend notification" in {
      val uuid = "resend"
      this ? (uuid, new SendNotification(_mail(uuid))) await {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (uuid, new ResendNotification(uuid)) await {
            case n: NotificationSent => n.uuid shouldBe uuid
            case _                   => fail()
          }
          this ? ("fake", new ResendNotification(uuid)) await {
            case NotificationNotFound => succeed
            case _                    => fail()
          }
        case _                    => fail()
      }
    }

    "retrieve notification status" in {
      val uuid = "status"
      this ? (uuid, new SendNotification(_mail(uuid))) await {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (uuid, new GetNotificationStatus(uuid)) await {
            case n: NotificationSent => n.uuid shouldBe uuid
            case _                   => fail()
          }
        case _                    => fail()
      }
    }

    "trigger notification" in {
      val uuid = "trigger"
      this ? (uuid, new SendNotification(_mail(uuid))) await {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (uuid, new GetNotificationStatus(uuid)) await {
            case n: NotificationSent =>
              n.uuid shouldBe uuid
              probe.expectMessage(Schedule4NotificationTriggered)
              succeed
            case _                   => fail()
          }
        case _                    => fail()
      }
    }
  }
}
