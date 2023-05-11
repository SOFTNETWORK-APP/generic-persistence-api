package app.softnetwork.api.server

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.{actor => classic, Done}
import app.softnetwork.config.Settings
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.persistence.typed._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/** Created by smanciot on 25/04/2020.
  */
trait ApiServer extends PersistenceGuardian with Server { _: SchemaProvider with ApiRoutes =>

  override def startSystem: ActorSystem[_] => Unit = system => {

    implicit val classicSystem: classic.ActorSystem = system

    val shutdown = CoordinatedShutdown(classicSystem)

    implicit val ec: ExecutionContextExecutor = classicSystem.dispatcher

    Http().newServerAt(interface, port).bind(mainRoutes(system)).onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        classicSystem.log.info(
          s"${classicSystem.name} application started at http://{}:{}/",
          address.getHostString,
          address.getPort
        )

        shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") {
          () =>
            binding.terminate(Settings.DefaultTimeout).map { _ =>
              classicSystem.log.info(
                s"${classicSystem.name} application http://{}:{}/ graceful shutdown completed",
                address.getHostString,
                address.getPort
              )
              Done
            }
        }
      case Failure(ex) =>
        classicSystem.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        classicSystem.terminate()
    }
  }
}
