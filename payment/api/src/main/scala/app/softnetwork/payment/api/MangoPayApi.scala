package app.softnetwork.payment.api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.notification.handlers.NotificationHandler
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.Scheduler2NotificationProcessorStream
import app.softnetwork.notification.peristence.typed.{AllNotificationsBehavior, NotificationBehavior}
import app.softnetwork.payment.handlers.MangoPayPaymentHandler
import app.softnetwork.payment.launch.PaymentApplication
import app.softnetwork.payment.persistence.query.{GenericPaymentCommandProcessorStream, Scheduler2PaymentProcessorStream}
import app.softnetwork.payment.persistence.typed.{GenericPaymentBehavior, MangoPayPaymentBehavior}
import app.softnetwork.payment.service.{GenericPaymentService, MangoPayPaymentService}
import app.softnetwork.persistence.jdbc.query.JdbcSchema.SchemaType
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream

trait MangoPayApi extends PaymentApplication with JdbcSchemaProvider {

  def jdbcSchemaType: SchemaType = this.schemaType

  override def paymentAccountBehavior: ActorSystem[_] => GenericPaymentBehavior = _ => MangoPayPaymentBehavior

  override def paymentCommandProcessorStream: ActorSystem[_] => GenericPaymentCommandProcessorStream = sys =>
    new GenericPaymentCommandProcessorStream with MangoPayPaymentHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override implicit def system: ActorSystem[_] = sys
      override def schemaType: JdbcSchema.SchemaType = jdbcSchemaType
    }

  override def notificationBehavior : ActorSystem[_] => NotificationBehavior[Notification] = _ =>
    AllNotificationsBehavior

  override def entity2SchedulerProcessorStream: ActorSystem[_] => Entity2SchedulerProcessorStream = sys =>
    new Entity2SchedulerProcessorStream() with SchedulerHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }

  override def scheduler2NotificationProcessorStream: ActorSystem[_] => Scheduler2NotificationProcessorStream = sys =>
    new Scheduler2NotificationProcessorStream() with NotificationHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override val tag = s"${AllNotificationsBehavior.persistenceId}-scheduler"
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit val system: ActorSystem[_] = sys
    }

  override def scheduler2PaymentProcessorStream: ActorSystem[_] => Scheduler2PaymentProcessorStream = sys =>
    new Scheduler2PaymentProcessorStream with MangoPayPaymentHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override val tag: String = s"${MangoPayPaymentBehavior.persistenceId}-scheduler"
      override implicit def system: ActorSystem[_] = sys
      override def schemaType: JdbcSchema.SchemaType = jdbcSchemaType
    }

  override def paymentService: ActorSystem[_] => GenericPaymentService = sys => MangoPayPaymentService(sys)

  override def apiRoutes(system: ActorSystem[_]): Route = super.apiRoutes(system) ~ BasicServiceRoute(system).route

}
