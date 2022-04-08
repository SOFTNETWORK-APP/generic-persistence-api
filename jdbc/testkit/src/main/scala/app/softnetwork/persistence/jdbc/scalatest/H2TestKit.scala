package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.query.H2SchemaProvider
import org.scalatest.TestSuite

trait H2TestKit extends TestSuite with H2SchemaProvider with JdbcPersistenceTestKit {

  import app.softnetwork.persistence._

  val H2Database: String = sys.env.getOrElse("H2_DATABASE", generateUUID())

  val H2User: String = sys.env.getOrElse("H2_USER", "admin")

  val H2Password: String = sys.env.getOrElse("H2_PASSWORD", "changeit")

  override lazy val slick: String = s"""
                      |slick {
                      |  profile = "slick.jdbc.PostgresProfile$$"
                      |  db {
                      |    url = "jdbc:h2:mem:$H2Database;DATABASE_TO_UPPER=false;"
                      |    user = "$H2User"
                      |    password = "$H2Password"
                      |    driver = "org.h2.Driver"
                      |    numThreads = 5
                      |    maxConnections = 5
                      |    minConnections = 1
                      |    idleTimeout = 10000 //10 seconds
                      |  }
                      |}
                      |""".stripMargin

}