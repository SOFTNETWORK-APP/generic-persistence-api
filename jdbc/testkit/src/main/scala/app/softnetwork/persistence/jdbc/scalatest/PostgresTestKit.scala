package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.schema.JdbcSchemaTypes.Postgres
import app.softnetwork.persistence.schema.SchemaType
import org.scalatest.Suite
import org.testcontainers.containers.{JdbcDatabaseContainer, PostgreSQLContainer}

trait PostgresTestKit extends JdbcContainerTestKit { _: Suite =>

  override val schemaType: SchemaType = Postgres

  def postgresVersion: String = "13"

  val maxConnections: Int = 200

  lazy val jdbcContainer: JdbcDatabaseContainer[_] = {
    val c = new PostgreSQLContainer(s"postgres:$postgresVersion")
    c.withCommand("postgres", "-c", s"max_connections=$maxConnections")
    c
  }

  override lazy val slickProfile: String = "slick.jdbc.PostgresProfile$"

  val jdbcDriver: String = "org.postgresql.Driver"

}
