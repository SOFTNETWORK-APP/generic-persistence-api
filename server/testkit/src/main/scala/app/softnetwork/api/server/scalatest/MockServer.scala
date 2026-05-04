package app.softnetwork.api.server.scalatest

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.typed._
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}

trait MockServer extends Completion {

  def log: Logger

  def name: String

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContext = system.executionContext

  protected def start(): Boolean

  protected def stop(): Future[Done]

  /** Start the mock server and register a shutdown hook to gracefully stop it when the application
    * is terminated.
    * @param coordinatedShutdown
    *   whether to register a shutdown hook with Akka's CoordinatedShutdown
    * @return
    *   true if the server was successfully started, false otherwise
    */
  final def init(coordinatedShutdown: Boolean = true): Boolean = {
    val started = start()
    if (started) {
      log.info(s"Mock Server $name started")
      if (coordinatedShutdown) {
        implicit val classicSystem: _root_.akka.actor.ActorSystem = system
        val shutdown = CoordinatedShutdown(classicSystem)
        shutdown.addTask(
          CoordinatedShutdown.PhaseServiceRequestsDone,
          s"$name-graceful-terminate"
        ) { () =>
          log.info(s"Stopping Mock Server $name ...")
          stop()
        }
      }
    }
    started
  }

  /** Gracefully shutdown the mock server, allowing it to complete any ongoing requests before
    * stopping.
    * @return
    *   a Future that completes when the server has been fully stopped
    */
  final def shutdown(): Future[Done] = {
    log.info(s"Shutting down Mock Server $name ...")
    stop()
  }
}
