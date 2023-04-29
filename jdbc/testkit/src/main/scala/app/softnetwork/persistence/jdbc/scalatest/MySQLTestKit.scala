package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.schema.MySQLSchemaProvider
import org.scalatest.Suite
import org.testcontainers.containers.MySQLContainer

trait MySQLTestKit extends JdbcContainerTestKit with MySQLSchemaProvider { _: Suite =>

  def mysqlVersion: String = "5.7"

  lazy val jdbcContainer = new MySQLContainer(s"mysql:$mysqlVersion")

  val slickProfile: String = "slick.jdbc.MySQLProfile$"

  val jdbcDriver: String = "com.mysql.cj.jdbc.Driver"

}
