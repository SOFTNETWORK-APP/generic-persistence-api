package app.softnetwork.api.server

import akka.actor.typed.ActorSystem

trait ApiEndpoints extends ApiRoutes {

  def endpoints: ActorSystem[_] => List[Endpoint]

  final override def apiRoutes: ActorSystem[_] => List[ApiRoute] = endpoints
}
