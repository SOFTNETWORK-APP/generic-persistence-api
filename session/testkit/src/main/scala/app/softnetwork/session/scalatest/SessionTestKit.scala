package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.InMemoryPersistenceScalatestRouteTest
import app.softnetwork.api.server.config.ServerSettings.RootPath
import app.softnetwork.api.server.{ApiRoutes, DefaultComplete}
import app.softnetwork.session.config.Settings
import app.softnetwork.session.launch.SessionGuardian
import app.softnetwork.session.model.SessionCompanion
import app.softnetwork.session.service.SessionService
import com.softwaremill.session.CsrfDirectives.{randomTokenCsrfProtection, setNewCsrfToken}
import com.softwaremill.session.CsrfOptions.checkHeader
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.Formats
import org.scalatest.Suite
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

trait SessionTestKit
    extends SessionServiceRoutes
    with InMemoryPersistenceScalatestRouteTest
    with SessionGuardian
    with SessionCompanion { _: Suite =>

  import app.softnetwork.serialization._

  var cookies: Seq[HttpHeader] = Seq.empty

  def withCookies(request: HttpMessage): request.Self = {
    request.withHeaders(request.headers ++ cookies: _*)
  }

  def createSession(
    id: String,
    profile: Option[String] = None,
    admin: Option[Boolean] = None
  ): Unit = {
    invalidateSession()
    Post(s"/$RootPath/session", CreateSession(id, profile, admin)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      cookies = extractCookies(headers)
    }
  }

  def extractSession(): Option[Session] = {
    cookies.flatMap(findCookie(Settings.Session.CookieName)(_)).headOption match {
      case Some(cookie) => sessionManager.clientSessionManager.decode(cookie.value).toOption
      case _            => None
    }
  }

  def invalidateSession(): Unit = {
    if (cookies.nonEmpty) {
      withCookies(Delete(s"/$RootPath/session")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        cookies = Seq.empty
      }
    }
  }
}

trait SessionServiceRoutes extends ApiRoutes {
  override def apiRoutes(system: ActorSystem[_]): Route = SessionServiceRoute(system).route
}

case class CreateSession(id: String, profile: Option[String] = None, admin: Option[Boolean] = None)

trait SessionServiceRoute
    extends SessionService
    with Directives
    with DefaultComplete
    with Json4sSupport {

  import app.softnetwork.serialization._

  import Session._

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
          randomTokenCsrfProtection(checkHeader) {
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
