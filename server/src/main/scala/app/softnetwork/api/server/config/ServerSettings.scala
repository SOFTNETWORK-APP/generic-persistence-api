package app.softnetwork.api.server.config

import com.typesafe.config.{Config, ConfigFactory}

object ServerSettings {

  lazy val config: Config =
    ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-api-server.conf"))

  val Interface: String = config.getString("softnetwork.api.server.interface")
  val Port: Int = config.getInt("softnetwork.api.server.port")
  val RootPath: String = config.getString("softnetwork.api.server.root-path")
}
