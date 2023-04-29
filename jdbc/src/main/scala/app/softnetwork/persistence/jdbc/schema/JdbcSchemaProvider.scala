package app.softnetwork.persistence.jdbc.schema

import app.softnetwork.persistence.jdbc.db.SlickDatabase
import app.softnetwork.persistence.jdbc.schema.JdbcSchema._
import app.softnetwork.persistence.schema.{SchemaProvider, SchemaType}

trait JdbcSchemaProvider extends SchemaProvider with SlickDatabase {

  override def create(schema: String, separator: String): Unit = withFile(schema, separator)

}

trait PostgresSchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = Postgres
}

trait H2SchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = H2
}

trait MySQLSchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = MySQL
}

trait OracleSchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = Oracle
}

trait SQLServerSchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = SQLServer
}
