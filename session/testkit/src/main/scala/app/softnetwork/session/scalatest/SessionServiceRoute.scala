package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.{ApiRoute, DefaultComplete}
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.{SessionMaterials, SessionService}
import com.softwaremill.session.CsrfDirectives.{hmacTokenCsrfProtection, setNewCsrfToken}
import com.softwaremill.session.SessionConfig
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.Formats

trait SessionServiceRoute[T <: SessionData with SessionDataDecorator[T]]
    extends SessionService[T]
    with Directives
    with DefaultComplete
    with Json4sSupport
    with ApiRoute { _: SessionMaterials[T] =>

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
            // create a new session
            setSession(sc, st, companion.newSession.withData(session.data)) {
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
