package app.softnetwork.api.server.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoutes, ApiServer}
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.schema.SchemaProvider
import com.typesafe.config.ConfigFactory

/** Created by smanciot on 22/03/2018.
  */
trait Application extends App with ApiServer with Completion { _: SchemaProvider with ApiRoutes =>

  lazy val config =
    ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-api-server.conf"))

  private[this] val systemName = config.getString("clustering.cluster.name")

  ActorSystem(setup(), systemName, config)

}
