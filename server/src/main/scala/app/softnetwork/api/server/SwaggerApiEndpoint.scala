package app.softnetwork.api.server

import akka.http.scaladsl.server.Route

trait SwaggerApiEndpoint extends ApiEndpoint with SwaggerEndpoint {

  override def route: Route = apiRoute ~ swaggerRoute
}
