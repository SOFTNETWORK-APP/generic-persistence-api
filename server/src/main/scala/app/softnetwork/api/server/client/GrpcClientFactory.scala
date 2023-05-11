package app.softnetwork.api.server.client

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem

import app.softnetwork.persistence.typed._

trait GrpcClientFactory[T <: GrpcClient] {
  def name: String
  private[this] var client: Option[T] = None
  def init(sys: ActorSystem[_]): T
  def apply(sys: ActorSystem[_]): T = {
    client match {
      case Some(value) => value
      case _ =>
        implicit val classicSystem: _root_.akka.actor.ActorSystem = sys
        val shutdown = CoordinatedShutdown(classicSystem)
        val cli = init(sys)
        client = Some(cli)
        shutdown.addTask(
          CoordinatedShutdown.PhaseServiceRequestsDone,
          s"$name-graceful-terminate"
        ) { () =>
          client = None
          cli.grpcClient.close()
        }
        cli
    }
  }
}
