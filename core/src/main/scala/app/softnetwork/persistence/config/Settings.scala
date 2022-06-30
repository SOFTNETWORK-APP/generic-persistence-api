package app.softnetwork.persistence.config

import com.typesafe.config.{Config, ConfigFactory}

import java.util

/**
  * Created by smanciot on 04/05/2021.
  */
object Settings {

  lazy val config: Config =
    ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-in-memory-persistence.conf"))

  val AkkaClusterSeedNodes: util.List[String] = config.getStringList("akka.cluster.seed-nodes")

  val AkkaClusterWithBootstrap: Boolean = AkkaClusterSeedNodes.isEmpty &&
    !config.getIsNull("akka.management.cluster.bootstrap.contact-point-discovery.discovery-method")
}
