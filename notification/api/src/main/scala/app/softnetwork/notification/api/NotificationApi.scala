package app.softnetwork.notification.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.launch.NotificationApplication
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.{NotificationCommandProcessorStream, Scheduler2NotificationProcessorStream}
import app.softnetwork.notification.peristence.typed.{AllNotificationsBehavior, NotificationBehavior}
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}
import app.softnetwork.scheduler.api.SchedulerApi
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream

trait NotificationApi extends SchedulerApi with NotificationApplication{

  override def notificationBehavior : ActorSystem[_] => NotificationBehavior[Notification] = _ =>
    AllNotificationsBehavior

  override def scheduler2NotificationProcessorStream: ActorSystem[_] => Scheduler2NotificationProcessorStream = sys =>
    new Scheduler2NotificationProcessorStream() with NotificationHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override val tag = s"${AllNotificationsBehavior.persistenceId}-scheduler"
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit val system: ActorSystem[_] = sys
    }

  override def notificationCommandProcessorStream: ActorSystem[_] => NotificationCommandProcessorStream = sys => {
    new NotificationCommandProcessorStream with NotificationHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }
  }

  override def scheduler2EntityProcessorStreams: ActorSystem[_] => Seq[Scheduler2EntityProcessorStream[_, _]] = sys =>
    Seq(
      scheduler2NotificationProcessorStream(sys)
    )

}
