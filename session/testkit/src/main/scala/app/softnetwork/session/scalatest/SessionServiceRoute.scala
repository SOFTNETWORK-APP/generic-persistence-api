package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.DefaultComplete
import app.softnetwork.session.service.SessionService
import com.softwaremill.session.CsrfDirectives.{hmacTokenCsrfProtection, setNewCsrfToken}
import com.softwaremill.session.CsrfOptions.checkHeader
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.Formats
import org.softnetwork.session.model.Session

trait SessionServiceRoute extends Directives with DefaultComplete with Json4sSupport {

  import Session._
  import app.softnetwork.serialization._

  implicit def formats: Formats = commonFormats

  def sessionService: SessionService

  val route: Route = {
    pathPrefix("session") {
      pathEnd {
        get {
          // check anti CSRF token
          hmacTokenCsrfProtection(checkHeader) {
            // check if a session exists
            sessionService.requiredSession { _ =>
              complete(StatusCodes.NotFound) // simulate an error
            }
          }
        } ~
        post {
          entity(as[CreateSession]) { session =>
            val s = Session(session.id)
            session.profile match {
              case Some(p) => s += (profileKey, p)
              case _       =>
            }
            session.admin match {
              case Some(a) => s += (adminKey, s"$a")
              case _       =>
            }
            // create a new session
            sessionService.setSession(s) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK))
              }
            }
          }
        } ~
        delete {
          // check anti CSRF token
          hmacTokenCsrfProtection(checkHeader) {
            // check if a session exists
            sessionService.requiredSession { _ =>
              // invalidate session
              sessionService.invalidateSession {
                complete(HttpResponse(StatusCodes.OK))
              }
            }
          }
        }
      }
    }
  }
}

object SessionServiceRoute {
  def apply(_sessionService: SessionService): SessionServiceRoute = {
    new SessionServiceRoute {
      override def sessionService: SessionService = _sessionService
    }
  }

  def oneOffCookie(system: ActorSystem[_]): SessionServiceRoute =
    SessionServiceRoute(SessionService.oneOffCookie(system))

  def oneOffHeader(system: ActorSystem[_]): SessionServiceRoute =
    SessionServiceRoute(SessionService.oneOffHeader(system))

  def refreshableCookie(system: ActorSystem[_]): SessionServiceRoute =
    SessionServiceRoute(SessionService.refreshableCookie(system))

  def refreshableHeader(system: ActorSystem[_]): SessionServiceRoute =
    SessionServiceRoute(SessionService.refreshableHeader(system))

}
