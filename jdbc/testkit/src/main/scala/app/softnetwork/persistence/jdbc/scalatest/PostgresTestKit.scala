package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.schema.PostgresSchemaProvider
import org.scalatest.Suite
import org.testcontainers.containers.PostgreSQLContainer

trait PostgresTestKit extends JdbcContainerTestKit with PostgresSchemaProvider { _: Suite =>

  def postgresVersion: String = "9.6"

  lazy val jdbcContainer = new PostgreSQLContainer(s"postgres:$postgresVersion")

  val slickProfile: String = "slick.jdbc.PostgresProfile$"

  val jdbcDriver: String = "org.postgresql.Driver"

}
