package app.softnetwork.elastic.config

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import configs.Configs
import app.softnetwork.elastic.client.ElasticCredentials

/**
  * Created by smanciot on 02/07/2018.
  */
object Settings extends StrictLogging {

  lazy val config: Option[ElasticConfig] = Configs[ElasticConfig].get(ConfigFactory.load(), "elastic").toEither match {
    case Left(configError) =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      None
    case Right(r) => Some(r)
  }

  case class ElasticConfig(
    credentials: ElasticCredentials,
    multithreaded: Boolean = true,
    discoveryEnabled: Boolean = false
  )

}
