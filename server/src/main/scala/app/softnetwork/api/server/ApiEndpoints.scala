package app.softnetwork.api.server

import akka.actor.typed.ActorSystem

trait ApiEndpoints extends ApiRoutes {

  def endpoints: ActorSystem[_] => List[ApiEndpoint]

  final override def apiRoutes: ActorSystem[_] => List[ApiRoute] = endpoints
}
