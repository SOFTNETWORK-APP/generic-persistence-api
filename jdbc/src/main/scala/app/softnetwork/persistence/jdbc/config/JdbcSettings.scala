package app.softnetwork.persistence.jdbc.config

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

/** Created by smanciot on 15/09/2020.
  */
object JdbcSettings extends StrictLogging {

  case class JdbcEventProcessorOffsets(schema: String, table: String)

  lazy val config: Config =
    ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-jdbc-persistence.conf"))

  lazy val jdbcEventProcessorOffsets: JdbcEventProcessorOffsets =
    Configs[JdbcEventProcessorOffsets].get(config, "jdbc-event-processor-offsets").toEither match {
      case Left(configError) =>
        logger.error(s"Something went wrong with the provided arguments $configError")
        throw configError.configException
      case Right(eventProcessorOffsets) => eventProcessorOffsets
    }

}
