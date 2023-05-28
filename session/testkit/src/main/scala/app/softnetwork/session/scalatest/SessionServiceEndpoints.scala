package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import app.softnetwork.api.server.ApiEndpoint
import app.softnetwork.session.service._
import org.json4s.Formats
import org.softnetwork.session.model.Session
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

trait SessionServiceEndpoints extends ApiEndpoint with RouteConcatenation {

  import Session._

  import app.softnetwork.serialization._

  implicit def formats: Formats = commonFormats

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

  val createSessionEndpoint: ServerEndpoint[Any, Future] = {
    sessionEndpoints.transport
      .setSession(endpoint.in(jsonBody[CreateSession]).description("the session to create"))
      .post
      .in("session")
      .out(sessionEndpoints.csrfCookie)
      .serverLogic(_ =>
        _ => Future.successful(Right(Some(sessionEndpoints.setNewCsrfToken().valueWithMeta)))
      )
  }

  val retrieveSessionEndpoint: ServerEndpoint[Any, Future] =
    sessionEndpoints
      .hmacTokenCsrfProtectionEndpoint(sessionEndpoints.transport.requiredSession)
      .get
      .in("session")
      .serverLogic(_ => _ => Future.successful(Right(())))

  val invalidateSessionEndpoint: ServerEndpoint[Any, Future] =
    sessionEndpoints.continuity
      .invalidateSession(
        sessionEndpoints.hmacTokenCsrfProtectionEndpoint(sessionEndpoints.transport.requiredSession)
      )
      .delete
      .in("session")
      .serverLogic(_ => _ => Future.successful(Right()))

  override def endpoints: List[ServerEndpoint[Any, Future]] =
    List(retrieveSessionEndpoint, invalidateSessionEndpoint, createSessionEndpoint)

  lazy val route: Route = apiRoute ~ swaggerRoute
}

object SessionServiceEndpoints {

  def apply(_system: ActorSystem[_], _sessionEndpoints: SessionEndpoints): SessionServiceEndpoints =
    new SessionServiceEndpoints {
      override implicit def system: ActorSystem[_] = _system
      override def sessionEndpoints: SessionEndpoints = _sessionEndpoints
    }

}
