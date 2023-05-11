package app.softnetwork.api.server.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.persistence.schema.SchemaProvider

trait HealthCheckApplication extends Application with ApiRoutes { _: SchemaProvider =>
  override def apiRoutes(system: ActorSystem[_]): Route = complete(StatusCodes.NotFound)
}
