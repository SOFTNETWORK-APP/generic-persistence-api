package app.softnetwork.persistence.jdbc.scalatest

import org.scalatest.Suite
import org.testcontainers.containers.JdbcDatabaseContainer

trait JdbcContainerTestKit extends JdbcPersistenceTestKit { _: Suite =>

  def jdbcContainer: JdbcDatabaseContainer[_]

  lazy val jdbcUrl: String = jdbcContainer.getJdbcUrl

  lazy val jdbcUser: String = jdbcContainer.getUsername

  lazy val jdbcPassword: String = jdbcContainer.getPassword

  override def beforeAll(): Unit = {
    jdbcContainer.start()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    jdbcContainer.stop()
  }

}
