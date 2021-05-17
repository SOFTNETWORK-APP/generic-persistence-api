package app.softnetwork.persistence.config

import com.typesafe.config.{ConfigFactory, Config}

/**
  * Created by smanciot on 04/05/2021.
  */
object Settings {

  lazy val config: Config =
    ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-in-memory-persistence.conf"))

  val AkkaClusterSeedNodes = config.getStringList("akka.cluster.seed-nodes")
}
