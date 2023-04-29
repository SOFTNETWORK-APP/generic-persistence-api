package app.softnetwork.persistence.jdbc.query

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

case class JdbcEventProcessorOffsets(schema: String, table: String)

object JdbcEventProcessorOffsets extends StrictLogging {
  def apply(config: Config): JdbcEventProcessorOffsets = {
    Configs[JdbcEventProcessorOffsets]
      .get(
        config.withFallback(ConfigFactory.load("softnetwork-jdbc-persistence.conf")),
        "jdbc-event-processor-offsets"
      )
      .toEither match {
      case Left(configError) =>
        logger.error(s"Something went wrong with the provided arguments $configError")
        throw configError.configException
      case Right(eventProcessorOffsets) => eventProcessorOffsets
    }
  }
}
