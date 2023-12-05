package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiEndpoint
import app.softnetwork.session.model.{
  SessionData,
  SessionDataCompanion,
  SessionDataDecorator,
  SessionDataKeys
}
import app.softnetwork.session.{SessionEndpoints => _, _}
import app.softnetwork.session.service._
import com.softwaremill.session.SessionConfig
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

trait SessionEndpointsRoute[T <: SessionData with SessionDataDecorator[T]]
    extends SessionEndpoints[T]
    with ApiEndpoint
    with SessionDataKeys { _: SessionMaterials[T] =>

  import app.softnetwork.serialization._

  implicit def sessionConfig: SessionConfig

  implicit def companion: SessionDataCompanion[T]

  implicit def f: CreateSession => Option[T] = session => {
    Some(companion.newSession.withData(session.data))
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
