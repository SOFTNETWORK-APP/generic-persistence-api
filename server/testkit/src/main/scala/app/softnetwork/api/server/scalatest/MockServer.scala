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

  final def init(): Boolean = {
    val started = start()
    if (started) {
      log.info(s"Mock Server $name started")
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
    started
  }

}
