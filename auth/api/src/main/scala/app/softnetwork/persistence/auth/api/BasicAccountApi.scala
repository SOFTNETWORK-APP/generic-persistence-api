package app.softnetwork.persistence.auth.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.Scheduler2NotificationProcessorStream
import app.softnetwork.notification.peristence.typed.{AllNotificationsBehavior, NotificationBehavior}
import app.softnetwork.persistence.auth.handlers.{AccountDao, BasicAccountDao, BasicAccountTypeKey}
import app.softnetwork.persistence.auth.launch.AccountApplication
import app.softnetwork.persistence.auth.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.persistence.auth.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.persistence.auth.persistence.typed.{AccountBehavior, BasicAccountBehavior}
import app.softnetwork.persistence.auth.service.{AccountService, BasicAccountService}
import app.softnetwork.persistence.jdbc.query.JdbcSchema.SchemaType
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream

trait BasicAccountApi extends AccountApplication[BasicAccount, BasicAccountProfile] with JdbcSchemaProvider {
  def jdbcSchemaType: SchemaType = this.schemaType

  override def accountDao: AccountDao = BasicAccountDao

  override def accountService: ActorSystem[_] => AccountService = sys => BasicAccountService(sys)

  override def accountBehavior: ActorSystem[_] => AccountBehavior[BasicAccount, BasicAccountProfile] = _ =>
    BasicAccountBehavior

  override def notificationBehavior : ActorSystem[_] => NotificationBehavior[Notification] = _ =>
    AllNotificationsBehavior

  override def internalAccountEvents2AccountProcessorStream: ActorSystem[_] => InternalAccountEvents2AccountProcessorStream = sys =>
    new InternalAccountEvents2AccountProcessorStream with BasicAccountTypeKey with JdbcJournalProvider with JdbcSchemaProvider {
      override def tag: String = s"${BasicAccountBehavior.persistenceId}-to-internal"
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }

  override def scheduler2NotificationProcessorStream: ActorSystem[_] => Scheduler2NotificationProcessorStream = sys =>
    new Scheduler2NotificationProcessorStream() with NotificationHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override val tag = s"${AllNotificationsBehavior.persistenceId}-scheduler"
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit val system: ActorSystem[_] = sys
    }

  override def entity2SchedulerProcessorStream: ActorSystem[_] => Entity2SchedulerProcessorStream = sys =>
    new Entity2SchedulerProcessorStream() with SchedulerHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }
}
