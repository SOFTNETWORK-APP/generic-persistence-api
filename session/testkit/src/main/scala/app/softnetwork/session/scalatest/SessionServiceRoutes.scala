package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes

trait SessionServiceRoutes extends ApiRoutes {
  override def apiRoutes(system: ActorSystem[_]): Route = SessionServiceRoute(system).route
}
