package app.softnetwork.resource.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.resource.service.GenericResourceService

trait ResourceRoutes extends ApiRoutes with ResourceGuardian {_: SchemaProvider =>

  def resourceService: ActorSystem[_] => GenericResourceService

  override def apiRoutes(system: ActorSystem[_]): Route = resourceService(system).route

}
