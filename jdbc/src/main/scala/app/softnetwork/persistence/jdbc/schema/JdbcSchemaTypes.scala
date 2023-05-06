package app.softnetwork.persistence.jdbc.schema

import app.softnetwork.persistence.schema.AbstractSchema

/** Created by smanciot on 07/05/2021.
  */
object JdbcSchemaTypes {

  case object Postgres extends AbstractSchema(schema = "schema/postgres/postgres-create-schema.sql")
  case object H2 extends AbstractSchema(schema = "schema/h2/h2-create-schema.sql")
  case object MySQL extends AbstractSchema(schema = "schema/mysql/mysql-create-schema.sql")
  case object Oracle extends AbstractSchema(schema = "schema/oracle/oracle-create-schema.sql")
  case object SQLServer
      extends AbstractSchema(schema = "schema/sqlserver/sqlserver-create-schema.sql")
}
