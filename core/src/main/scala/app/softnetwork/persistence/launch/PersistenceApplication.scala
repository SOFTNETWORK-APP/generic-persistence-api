package app.softnetwork.persistence.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.schema.SchemaProvider
import com.typesafe.config.{Config, ConfigFactory}

trait PersistenceApplication extends App with PersistenceGuardian { _: SchemaProvider =>
  private[this] val systemName = appConfig.getString("clustering.cluster.name")

  lazy val appConfig: Config = ConfigFactory.load()

  ActorSystem(setup(), systemName, appConfig)

}
