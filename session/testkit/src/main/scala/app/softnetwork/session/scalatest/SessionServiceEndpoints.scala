package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import app.softnetwork.api.server.ApiEndpoint
import app.softnetwork.session.service._
import com.softwaremill.session.{
  CsrfCheckHeaderAndForm,
  SessionContinuityEndpoints,
  SessionTransportEndpoints
}
import org.json4s.Formats
import org.softnetwork.session.model.Session
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

trait SessionServiceEndpoints
    extends ApiEndpoint
    with SessionEndpoints
    with CsrfCheckHeaderAndForm
    with RouteConcatenation {
  _: SessionTransportEndpoints[Session] with SessionContinuityEndpoints[Session] =>
  import Session._

  import app.softnetwork.serialization._

  implicit def formats: Formats = commonFormats

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
    setSession(endpoint.in(jsonBody[CreateSession]))
      .in("session")
      .out(csrfCookie)
      .serverLogic(_ => _ => Future.successful(Right(Some(setNewCsrfToken().valueWithMeta))))
  }

  val retrieveSessionEndpoint: ServerEndpoint[Any, Future] =
    hmacTokenCsrfProtectionEndpoint(requiredSession).get
      .in("session")
      .serverLogic(_ => _ => Future.successful(Right(())))

  val invalidateSessionEndpoint: ServerEndpoint[Any, Future] =
    invalidateSession(
      hmacTokenCsrfProtectionEndpoint(requiredSession)
    ).delete
      .in("session")
      .serverLogic(_ => _ => Future.successful(Right()))

  override def endpoints: List[ServerEndpoint[Any, Future]] =
    List(retrieveSessionEndpoint, invalidateSessionEndpoint, createSessionEndpoint)

  lazy val route: Route = apiRoute ~ swaggerRoute
}

object RefreshableCookieSessionServiceEndpoints {
  def apply(_system: ActorSystem[_]): SessionServiceEndpoints =
    new SessionServiceEndpoints with RefreshableCookieSessionEndpoints {
      override implicit def system: ActorSystem[_] = _system
    }
}

object OneOffCookieSessionServiceEndpoints {
  def apply(_system: ActorSystem[_]): SessionServiceEndpoints =
    new SessionServiceEndpoints with OneOffCookieSessionEndpoints {
      override implicit def system: ActorSystem[_] = _system
    }
}
