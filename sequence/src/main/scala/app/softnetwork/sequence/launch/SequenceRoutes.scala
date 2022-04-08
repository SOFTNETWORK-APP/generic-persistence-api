package app.softnetwork.sequence.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.sequence.service.SequenceService

/**
  * Created by smanciot on 07/04/2022.
  */
trait SequenceRoutes extends ApiRoutes {

  override def apiRoutes(system: ActorSystem[_]): Route = SequenceService(system).route

}
