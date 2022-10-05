package app.softnetwork.api.server.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.{ApiRoutes, ApiServer, GrpcServices}
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.query.SchemaProvider
import com.typesafe.config.ConfigFactory

/**
  * Created by smanciot on 22/03/2018.
  */
trait Application extends App with ApiServer with Completion {_: ApiRoutes with GrpcServices with SchemaProvider =>

  private[this] val systemName = appConfig.getString ("clustering.cluster.name")

  lazy val appConfig = ConfigFactory.load()

  ActorSystem(setup(), systemName, appConfig)

}
