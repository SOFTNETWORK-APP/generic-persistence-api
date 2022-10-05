package app.softnetwork.api.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

trait GrpcServices {

  def mainServices: ActorSystem[_] => Seq[PartialFunction[HttpRequest, Future[HttpResponse]]] = _ => Seq.empty

}
