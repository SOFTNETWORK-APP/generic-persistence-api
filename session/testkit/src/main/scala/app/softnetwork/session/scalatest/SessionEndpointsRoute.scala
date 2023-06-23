package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import app.softnetwork.api.server.ApiEndpoint
import app.softnetwork.session.service._
import com.softwaremill.session.{SessionEndpoints => _, _}
import org.softnetwork.session.model.Session
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.server.{PartialServerEndpointWithSecurityOutput, ServerEndpoint}

import scala.concurrent.{ExecutionContext, Future}

trait SessionEndpointsRoute extends ApiEndpoint with RouteConcatenation {

  import Session._

  import app.softnetwork.serialization._

  implicit def system: ActorSystem[_]

  implicit def ec: ExecutionContext = system.executionContext

  def sessionEndpoints: SessionEndpoints

  implicit def f: CreateSession => Option[Session] = session => {
    val s = Session(session.id)
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

  def sc: TapirSessionContinuity[Session] = sessionEndpoints.sc

  def st: SetSessionTransport = sessionEndpoints.st

  def gt: GetSessionTransport = sessionEndpoints.gt

  def checkMode: TapirCsrfCheckMode[Session] = sessionEndpoints.checkMode

  val createSessionEndpoint: ServerEndpoint[Any, Future] = {
    import TapirImplicits._
    sessionEndpoints
      .setSession(sc, st) {
        endpoint.securityIn(jsonBody[CreateSession]).description("the session to create")
      }
      .post
      .in("session")
      .out(
        setCookieOpt(sessionEndpoints.manager.config.csrfCookieConfig.name)
          .description("set csrf token as cookie")
      )
      .serverLogic(_ =>
        _ =>
          Future.successful(
            Right(Some(sessionEndpoints.manager.csrfManager.createCookie().valueWithMeta))
          )
      )
  }

  val retrieveSessionEndpoint: ServerEndpoint[Any, Future] =
    sessionEndpoints
      .antiCsrfWithRequiredSession(sc, st, checkMode)
      .get
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

  val invalidateSessionEndpoint: ServerEndpoint[Any, Future] =
    sessionEndpoints
      .invalidateSession(sc, gt)(
        sessionEndpoints.antiCsrfWithRequiredSession(sc, st, checkMode)
      )
      .delete
      .in("session")
      .serverLogic(_ => _ => Future.successful(Right()))

  override def endpoints: List[ServerEndpoint[Any, Future]] =
    List(retrieveSessionEndpoint, invalidateSessionEndpoint, createSessionEndpoint)

  lazy val route: Route = apiRoute ~ swaggerRoute
}

object SessionEndpointsRoute {

  def apply(_system: ActorSystem[_], _sessionEndpoints: SessionEndpoints): SessionEndpointsRoute =
    new SessionEndpointsRoute {
      override implicit def system: ActorSystem[_] = _system
      override def sessionEndpoints: SessionEndpoints = _sessionEndpoints
    }

}
