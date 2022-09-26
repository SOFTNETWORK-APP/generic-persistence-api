package app.softnetwork.notification.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.launch.NotificationApplication
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.{NotificationCommandProcessorStream, Scheduler2NotificationProcessorStream}
import app.softnetwork.notification.peristence.typed.{AllNotificationsBehavior, NotificationBehavior}
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}
import app.softnetwork.scheduler.api.SchedulerApi

trait NotificationApi extends SchedulerApi with NotificationApplication {

  override def notificationBehavior : ActorSystem[_] => Option[NotificationBehavior[Notification]] = _ =>
    Some(AllNotificationsBehavior)

  override def scheduler2NotificationProcessorStream: ActorSystem[_] => Option[Scheduler2NotificationProcessorStream] =
    sys =>
      Some(
        new Scheduler2NotificationProcessorStream() with NotificationHandler with JdbcJournalProvider with JdbcSchemaProvider {
          override val tag = s"${AllNotificationsBehavior.persistenceId}-scheduler"
          override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
          override implicit val system: ActorSystem[_] = sys
        }
      )

  override def notificationCommandProcessorStream: ActorSystem[_] => Option[NotificationCommandProcessorStream] =
    sys =>
      Some(
        new NotificationCommandProcessorStream with NotificationHandler with JdbcJournalProvider with JdbcSchemaProvider {
          override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
          override implicit def system: ActorSystem[_] = sys
        }
      )

}
