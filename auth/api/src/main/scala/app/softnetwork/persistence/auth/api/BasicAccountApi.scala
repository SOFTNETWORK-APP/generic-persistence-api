package app.softnetwork.persistence.auth.api

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.api.NotificationApi
import app.softnetwork.persistence.auth.handlers.{AccountDao, BasicAccountDao, BasicAccountTypeKey}
import app.softnetwork.persistence.auth.launch.AccountApplication
import app.softnetwork.persistence.auth.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.persistence.auth.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.persistence.auth.persistence.typed.{AccountBehavior, BasicAccountBehavior}
import app.softnetwork.persistence.auth.service.{AccountService, BasicAccountService}
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}

trait BasicAccountApi extends NotificationApi with AccountApplication[BasicAccount, BasicAccountProfile] with JdbcSchemaProvider {

  override def accountDao: AccountDao = BasicAccountDao

  override def accountService: ActorSystem[_] => AccountService = sys => BasicAccountService(sys)

  override def accountBehavior: ActorSystem[_] => AccountBehavior[BasicAccount, BasicAccountProfile] = _ =>
    BasicAccountBehavior

  override def internalAccountEvents2AccountProcessorStream: ActorSystem[_] => InternalAccountEvents2AccountProcessorStream = sys =>
    new InternalAccountEvents2AccountProcessorStream with BasicAccountTypeKey with JdbcJournalProvider with JdbcSchemaProvider {
      override def tag: String = s"${BasicAccountBehavior.persistenceId}-to-internal"
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }

}
