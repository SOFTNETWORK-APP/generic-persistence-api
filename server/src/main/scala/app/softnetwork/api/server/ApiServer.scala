package app.softnetwork.api.server

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.{Done, actor => classic}
import app.softnetwork.api.server.config.Settings._
import app.softnetwork.config.Settings
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.query.SchemaProvider

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/**
  * Created by smanciot on 25/04/2020.
  */
trait ApiServer extends PersistenceGuardian {_: ApiRoutes with SchemaProvider =>

  /**
    *
    * initialize all server routes
    *
    */
  def routes: ActorSystem[_] => Route = system => mainRoutes(system)

  override def startSystem: ActorSystem[_] => Unit = system => {
    import app.softnetwork.persistence.typed._

    implicit val classicSystem: classic.ActorSystem = system

    val shutdown = CoordinatedShutdown(classicSystem)

    implicit val ec: ExecutionContextExecutor = classicSystem.dispatcher

    Http().bindAndHandle(mainRoutes(system), Interface, Port).onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        classicSystem.log.info(
          s"${classicSystem.name} application started at http://{}:{}/",
          address.getHostString,
          address.getPort
        )

        shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
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
