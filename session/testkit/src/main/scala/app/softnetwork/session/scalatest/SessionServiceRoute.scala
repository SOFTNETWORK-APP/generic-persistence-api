package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.{ApiRoute, DefaultComplete}
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataKeys}
import app.softnetwork.session.service.{SessionMaterials, SessionService}
import com.softwaremill.session.CsrfDirectives.{hmacTokenCsrfProtection, setNewCsrfToken}
import com.softwaremill.session.SessionConfig
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.Formats

trait SessionServiceRoute[T <: SessionData]
    extends SessionService[T]
    with Directives
    with DefaultComplete
    with Json4sSupport
    with ApiRoute
    with SessionDataKeys { _: SessionMaterials[T] =>

  import app.softnetwork.serialization._

  implicit def companion: SessionDataCompanion[T]

  implicit def formats: Formats = commonFormats

  implicit def sessionConfig: SessionConfig

  val route: Route = {
    pathPrefix("session") {
      pathEnd {
        get {
          // check anti CSRF token
          hmacTokenCsrfProtection(checkMode) {
            // check if a session exists
            requiredSession(sc, gt) { _ =>
              complete(StatusCodes.NotFound) // simulate an error
            }
          }
        } ~
        post {
          entity(as[CreateSession]) { session =>
            var s = companion.newSession.withId(session.id)
            session.profile match {
              case Some(p) => s += (profileKey, p)
              case _       =>
            }
            session.admin match {
              case Some(a) => s += (adminKey, s"$a")
              case _       =>
            }
            // create a new session
            setSession(sc, st, s) {
              // create a new anti csrf token
              setNewCsrfToken(checkMode) {
                complete(HttpResponse(StatusCodes.OK))
              }
            }
          }
        } ~
        delete {
          // check anti CSRF token
          hmacTokenCsrfProtection(checkMode) {
            // check if a session exists
            requiredSession(sc, gt) { _ =>
              // invalidate session
              invalidateSession(sc, gt) {
                complete(HttpResponse(StatusCodes.OK))
              }
            }
          }
        }
      }
    }
  }
}
