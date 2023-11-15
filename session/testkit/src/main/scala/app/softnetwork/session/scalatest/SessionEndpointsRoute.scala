package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiEndpoint
import app.softnetwork.session.{SessionEndpoints => _, _}
import app.softnetwork.session.service._
import com.softwaremill.session.SessionConfig
import org.softnetwork.session.model.Session
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

trait SessionEndpointsRoute extends SessionEndpoints with ApiEndpoint { _: SessionMaterials =>

  import Session._

  import app.softnetwork.serialization._

  implicit def sessionConfig: SessionConfig

  implicit def f: CreateSession => Option[Session] = session => {
    var s = Session(session.id)
    session.profile match {
      case Some(p) => s += (profileKey, p)
      case _       =>
    }
    session.admin match {
      case Some(a) => s += (adminKey, s"$a")
      case _       =>
    }
    Some(s)
  }

  val createSessionEndpoint: ServerEndpoint[Any, Future] = {
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpointToPartialServerEndpointWithSecurityOutput(
          endpoint.securityIn(jsonBody[CreateSession].description("the session to create"))
        )
      }
    }.post
      .in("session")
      .serverLogicSuccess(_ => _ => Future.successful(()))
  }

  val retrieveSessionEndpoint: ServerEndpoint[Any, Future] = {
    hmacTokenCsrfProtection(checkMode) {
      requiredSession(sc, st)
    }.get
      .in("session")
      .out(
        oneOf[BusinessError](
          oneOfVariant[NotFound.type](
            statusCode(StatusCode.NotFound)
              .and(emptyOutputAs(NotFound).description("Not found"))
          )
        )
      )
      .serverLogic(_ => _ => Future.successful(Right(NotFound))) // simulate an error
  }

  val invalidateSessionEndpoint: ServerEndpoint[Any, Future] = {
    invalidateSession(sc, gt)(
      hmacTokenCsrfProtection(checkMode) {
        requiredSession(sc, st)
      }
    ).delete
      .in("session")
      .serverLogic(_ => _ => Future.successful(Right(())))
  }

  override def endpoints: List[ServerEndpoint[Any, Future]] =
    List(retrieveSessionEndpoint, invalidateSessionEndpoint, createSessionEndpoint)

}
