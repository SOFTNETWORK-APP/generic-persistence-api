package app.softnetwork.notification.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.config.Settings.NotificationConfig
import app.softnetwork.notification.handlers.MockNotificationHandler
import app.softnetwork.notification.launch.NotificationGuardian
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.Scheduler2NotificationProcessorStream
import app.softnetwork.notification.peristence.typed.{MockAllNotificationsBehavior, NotificationBehavior}
import app.softnetwork.persistence.query.InMemoryJournalProvider
import app.softnetwork.scheduler.scalatest.SchedulerTestKit
import org.scalatest.Suite

trait NotificationTestKit extends SchedulerTestKit with NotificationGuardian {_: Suite =>

  /**
    *
    * @return roles associated with this node
    */
  override def roles: Seq[String] = super.roles :+ NotificationConfig.akkaNodeRole

  override def notificationBehavior: ActorSystem[_] => NotificationBehavior[Notification] = _ => MockAllNotificationsBehavior

  override def scheduler2NotificationProcessorStream: ActorSystem[_] => Scheduler2NotificationProcessorStream = sys =>
    new Scheduler2NotificationProcessorStream with MockNotificationHandler with InMemoryJournalProvider {
      override val tag: String = s"${MockAllNotificationsBehavior.persistenceId}-scheduler"
      override protected val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
    }

}
