package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

trait GrpcService extends Directives {

  def grpcService: ActorSystem[_] => PartialFunction[HttpRequest, Future[HttpResponse]]

  def route: ActorSystem[_] => Route = system => handle(grpcService(system))
}
