package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

trait ApiEndpoints extends ApiEndpoint with ApiRoutes {

  override def apiRoutes(system: ActorSystem[_]): Route = {
    implicit def ec: ExecutionContext = system.executionContext
    apiRoute ~ swaggerRoute
  }

}
