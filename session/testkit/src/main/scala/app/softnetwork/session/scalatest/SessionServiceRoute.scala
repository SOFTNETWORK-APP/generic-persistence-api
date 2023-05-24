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

import scala.concurrent.ExecutionContext

trait SessionServiceRoute
    extends SessionService
    with Directives
    with DefaultComplete
    with Json4sSupport {

  import Session._
  import app.softnetwork.serialization._

  implicit def formats: Formats = commonFormats

  implicit lazy val ec: ExecutionContext = system.executionContext

  val route: Route = {
    pathPrefix("session") {
      pathEnd {
        get {
          complete(StatusCodes.OK)
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
            sessionToDirective(s)(ec) {
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
            _requiredSession(ec) { _ =>
              // invalidate session
              _invalidateSession(ec) {
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
  def apply(_system: ActorSystem[_]): SessionServiceRoute = {
    new SessionServiceRoute {
      override implicit def system: ActorSystem[_] = _system
    }
  }
}
