package app.softnetwork.session.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.service.{SessionEndpoints, SessionService}
import org.softnetwork.session.model.Session

trait SessionServiceAndEndpoints { _: CsrfCheck =>

  protected def sessionType: Session.SessionType = Session.SessionType.OneOffCookie

  final def sessionService: ActorSystem[_] => SessionService = system =>
    SessionService(system, sessionType)

  final def sessionEndpoints: ActorSystem[_] => SessionEndpoints = system =>
    SessionEndpoints(system, sessionType, checkHeaderAndForm)

}
