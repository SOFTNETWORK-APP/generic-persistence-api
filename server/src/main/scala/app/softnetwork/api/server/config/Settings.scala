package app.softnetwork.api.server.config

import com.typesafe.config.{Config, ConfigFactory}

object Settings {

  lazy val config: Config = ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-api-server.conf"))

  val Interface = config.getString("softnetwork.api.server.interface")
  val Port      = config.getInt("softnetwork.api.server.port")
  val RootPath  = config.getString("softnetwork.api.server.root-path")
}
