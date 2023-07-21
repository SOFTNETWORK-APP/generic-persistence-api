package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

trait ApiEndpoints extends ApiRoutes {

  def endpoints: ActorSystem[_] => List[ApiEndpoint]

  override def apiRoutes(system: ActorSystem[_]): Route = {
    implicit def ec: ExecutionContext = system.executionContext
    concat(endpoints(system).map(api => api.apiRoute ~ api.swaggerRoute): _*)
  }

}
