package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.schema.H2SchemaProvider
import org.scalatest.Suite

trait H2TestKit extends JdbcPersistenceTestKit with H2SchemaProvider { _: Suite =>

  import app.softnetwork.persistence._

  val database: String = sys.env.getOrElse("H2_DATABASE", generateUUID())

  val slickProfile: String = "slick.jdbc.H2Profile$"

  val jdbcUser: String = sys.env.getOrElse("H2_USER", "admin")

  val jdbcPassword: String = sys.env.getOrElse("H2_PASSWORD", "changeit")

  lazy val jdbcUrl: String = s"jdbc:h2:mem:$database;DATABASE_TO_UPPER=false;"

  val jdbcDriver: String = "org.h2.Driver"
}
