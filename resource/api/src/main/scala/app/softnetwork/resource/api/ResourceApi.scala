package app.softnetwork.resource.api

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.JdbcSchema.SchemaType
import app.softnetwork.persistence.jdbc.query.JdbcSchemaProvider
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.resource.launch.GenericResourceApplication
import app.softnetwork.resource.message.ResourceEvents.ResourceEvent
import app.softnetwork.resource.message.ResourceMessages.{ResourceCommand, ResourceResult}
import app.softnetwork.resource.model.Resource
import app.softnetwork.resource.persistence.typed.ResourceBehavior

trait ResourceApi extends GenericResourceApplication[Resource] with JdbcSchemaProvider{
  def jdbcSchemaType: SchemaType = this.schemaType

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  override def resourceEntity: ActorSystem[_]
    => PersistentEntity[ResourceCommand, Resource, ResourceEvent, ResourceResult] = _ => ResourceBehavior
}
