package app.softnetwork.persistence.jdbc.schema

import app.softnetwork.persistence.jdbc.db.SlickDatabase
import app.softnetwork.persistence.schema.Schema

trait JdbcSchema extends Schema with SlickDatabase {

  override def create(schema: String, separator: String): Unit = withFile(schema, separator)

}
