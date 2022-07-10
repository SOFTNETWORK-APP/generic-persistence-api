package app.softnetwork.resource.api

import app.softnetwork.persistence.jdbc.query.JdbcSchema.SchemaType
import app.softnetwork.persistence.jdbc.query.JdbcSchemaProvider
import app.softnetwork.resource.launch.ResourceApplication

trait ResourceApi extends ResourceApplication with JdbcSchemaProvider{
  def jdbcSchemaType: SchemaType = this.schemaType
}
