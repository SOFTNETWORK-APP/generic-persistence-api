package app.softnetwork.scheduler.api

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.JdbcSchema.SchemaType
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.launch.SchedulerApplication
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream

trait SchedulerApi extends SchedulerApplication with JdbcSchemaProvider {

  def jdbcSchemaType: SchemaType = this.schemaType

  override def entity2SchedulerProcessorStream: ActorSystem[_] => Entity2SchedulerProcessorStream = sys =>
    new Entity2SchedulerProcessorStream() with SchedulerHandler with JdbcJournalProvider with JdbcSchemaProvider {
      override lazy val schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      override implicit def system: ActorSystem[_] = sys
    }

}
