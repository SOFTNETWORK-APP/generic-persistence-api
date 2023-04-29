package app.softnetwork.persistence.jdbc.config

/** Created by smanciot on 15/09/2020.
  */
object JdbcSettings {

  case class JdbcEventProcessorOffsets(schema: String, table: String)

}
