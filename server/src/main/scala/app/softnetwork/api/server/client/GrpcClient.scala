package app.softnetwork.api.server.client

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.AkkaGrpcClient

import scala.concurrent.ExecutionContext

trait GrpcClient {

  implicit def system: ActorSystem[_]
  implicit lazy val ec: ExecutionContext = system.executionContext

  def name: String

  def grpcClient: AkkaGrpcClient
}
