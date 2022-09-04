package app.softnetwork.resource.api

import app.softnetwork.persistence.jdbc.query.JdbcSchema.SchemaType
import app.softnetwork.persistence.jdbc.query.JdbcSchemaProvider
import app.softnetwork.resource.launch.GenericResourceApplication
import app.softnetwork.resource.model.Resource

trait ResourceApi extends GenericResourceApplication[Resource] with JdbcSchemaProvider{
  def jdbcSchemaType: SchemaType = this.schemaType
}
