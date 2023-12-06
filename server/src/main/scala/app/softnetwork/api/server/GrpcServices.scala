package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.{Route, RouteConcatenation}

trait GrpcServices extends RouteConcatenation {

  def grpcServices: ActorSystem[_] => Seq[GrpcService] = _ => Seq.empty

  final def grpcRoutes: ActorSystem[_] => Route = system =>
    concat(grpcServices(system).map(_.route(system)): _*)

}
