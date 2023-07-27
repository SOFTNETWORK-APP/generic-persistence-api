package app.softnetwork.api.server

import akka.http.scaladsl.server.{Route, RouteConcatenation}

trait ApiRoute extends RouteConcatenation {

  def route: Route

}
