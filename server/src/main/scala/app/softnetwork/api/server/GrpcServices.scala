package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives.handle
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

trait GrpcServices {

  def grpcServices: ActorSystem[_] => Seq[PartialFunction[HttpRequest, Future[HttpResponse]]] = _ =>
    Seq.empty

  final def grpcRoutes: ActorSystem[_] => Route = system =>
    handle(
      ServiceHandler.concatOrNotFound(
        grpcServices(system): _*
      )
    )

}
