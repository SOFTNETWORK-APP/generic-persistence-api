package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.schema.JdbcSchemaTypes.Postgres
import app.softnetwork.persistence.schema.SchemaType
import org.scalatest.Suite
import org.testcontainers.containers.PostgreSQLContainer

trait PostgresTestKit extends JdbcContainerTestKit { _: Suite =>

  override val schemaType: SchemaType = Postgres

  def postgresVersion: String = "13"

  lazy val jdbcContainer = new PostgreSQLContainer(s"postgres:$postgresVersion")

  val slickProfile: String = "slick.jdbc.PostgresProfile$"

  val jdbcDriver: String = "org.postgresql.Driver"

}
