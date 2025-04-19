package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.schema._
import app.softnetwork.persistence.jdbc.schema.JdbcSchemaTypes.H2
import org.scalatest.Suite

trait H2TestKit extends JdbcPersistenceTestKit { _: Suite =>

  override val schemaType: SchemaType = H2

  import app.softnetwork.persistence._

  val database: String = sys.env.getOrElse("H2_DATABASE", generateUUID())

  override lazy val slickProfile: String = "slick.jdbc.H2Profile$"

  val jdbcUser: String = sys.env.getOrElse("H2_USER", "admin")

  val jdbcPassword: String = sys.env.getOrElse("H2_PASSWORD", "changeit")

  lazy val jdbcUrl: String = s"jdbc:h2:mem:$database;DATABASE_TO_UPPER=false;"

  val jdbcDriver: String = "org.h2.Driver"
}
