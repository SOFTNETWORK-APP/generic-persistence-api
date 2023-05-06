package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.schema.JdbcSchemaTypes.MySQL
import app.softnetwork.persistence.schema.SchemaType
import org.scalatest.Suite
import org.testcontainers.containers.MySQLContainer

trait MySQLTestKit extends JdbcContainerTestKit { _: Suite =>

  override val schemaType: SchemaType = MySQL

  def mysqlVersion: String = "5.7"

  lazy val jdbcContainer = new MySQLContainer(s"mysql:$mysqlVersion")

  val slickProfile: String = "slick.jdbc.MySQLProfile$"

  val jdbcDriver: String = "com.mysql.cj.jdbc.Driver"

}
