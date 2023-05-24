package app.softnetwork.persistence.jdbc.schema

import akka.actor
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.schema.{Schema, SchemaProvider, SchemaType}
import app.softnetwork.persistence.typed._
import com.typesafe.config.Config

trait JdbcSchemaProvider extends SchemaProvider { _: PersistenceGuardian =>

  def schemaType: SchemaType

  override def schema: ActorSystem[_] => Schema = sys =>
    new JdbcSchema {
      override def schemaType: SchemaType = JdbcSchemaProvider.this.schemaType
      override implicit def classicSystem: actor.ActorSystem = sys
      override def config: Config = JdbcSchemaProvider.this.config
    }
}
