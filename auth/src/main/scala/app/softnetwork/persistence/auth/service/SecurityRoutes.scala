package app.softnetwork.persistence.auth.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes

/**
  * Created by smanciot on 24/04/2020.
  */
trait SecurityRoutes extends ApiRoutes {

  override def apiRoutes(system: ActorSystem[_]): Route = BasicAccountService(system).route

}

trait MockSecurityRoutes extends ApiRoutes {

  override def apiRoutes(system: ActorSystem[_]): Route = MockBasicAccountService(system).route

}