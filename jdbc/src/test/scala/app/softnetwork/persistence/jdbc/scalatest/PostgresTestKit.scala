package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.docker.PostgresService
import app.softnetwork.persistence.jdbc.query.PostgresSchemaProvider

trait PostgresTestKit extends PostgresService with PostgresSchemaProvider with JdbcPersistenceTestKit {

  override lazy val slick = s"""
                      |slick {
                      |  profile = "slick.jdbc.PostgresProfile$$"
                      |  db {
                      |    url = "jdbc:postgresql://$PostgresHost:$PostgresPort/$PostgresDB?reWriteBatchedInserts=true"
                      |    user = "$PostgresUser"
                      |    password = "$PostgresPassword"
                      |    driver = "org.postgresql.Driver"
                      |    numThreads = 5
                      |    maxConnections = 5
                      |    minConnections = 1
                      |    idleTimeout = 10000 //10 seconds
                      |  }
                      |}
                      |""".stripMargin

  override def beforeAll(): Unit = {
    startAllOrFail()
    waitForContainerUp()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }

}