package app.softnetwork.api.server

import akka.{Done, actor => classic}
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.Http
import app.softnetwork.api.server.config.Settings.{Interface, Port}
import app.softnetwork.config.Settings
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.query.SchemaProvider
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

trait GrpcServer extends PersistenceGuardian with StrictLogging {_: GrpcServices with SchemaProvider =>

  lazy val interface: String = Interface

  lazy val port: Int = Port

  override def startSystem: ActorSystem[_] => Unit = system => {
    import app.softnetwork.persistence.typed._

    implicit val classicSystem: classic.ActorSystem = system

    val shutdown = CoordinatedShutdown(classicSystem)

    implicit val ec: ExecutionContextExecutor = classicSystem.dispatcher

    Http().newServerAt(interface, port).bind(ServiceHandler.concatOrNotFound(mainServices(system):_*)).onComplete {
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

