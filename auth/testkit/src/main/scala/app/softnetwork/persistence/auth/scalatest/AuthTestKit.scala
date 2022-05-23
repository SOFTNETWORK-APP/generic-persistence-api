package app.softnetwork.persistence.auth.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, StatusCodes}
import akka.http.scaladsl.testkit.PersistenceScalatestRouteTest
import app.softnetwork.api.server.config.Settings.RootPath
import app.softnetwork.persistence.auth.config.Settings.Path
import app.softnetwork.persistence.auth.handlers.MockBasicAccountDao
import app.softnetwork.persistence.auth.message._
import app.softnetwork.persistence.auth.persistence.typed.MockBasicAccountBehavior
import app.softnetwork.persistence.auth.serialization.authFormats
import app.softnetwork.persistence.auth.service.MockSecurityRoutes
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import app.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior
import org.json4s.Formats
import org.scalatest.Suite

import scala.util.{Failure, Success}

trait AuthTestKit extends InMemoryPersistenceTestKit {_: Suite =>
  implicit lazy val tsystem: ActorSystem[_] = typedSystem()

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    MockBasicAccountBehavior,
    SessionRefreshTokenBehavior,
    SchedulerBehavior
  )

}

trait AuthRouteTestKit extends MockSecurityRoutes with PersistenceScalatestRouteTest with AuthTestKit {
  _: Suite =>

  import app.softnetwork.serialization._

  override implicit def formats: Formats = authFormats

  var cookies: Seq[HttpHeader] = Seq.empty

  def signUp(uuid: String, login: String, password: String): Boolean = {
    MockBasicAccountDao ??(uuid, SignUp(login, password)) await {
      case _: AccountCreated => true
      case _ => false
    } match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }

  def signIn(login: String, password: String): Unit = {
    if(cookies.nonEmpty) signOut()
    Post(s"/$RootPath/$Path/signIn", Login(login, password)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      cookies = extractCookies(headers)
    }
  }

  def signOut(): Unit = {
    Post(s"/$RootPath/$Path/signOut", Logout) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      cookies = Seq.empty
    }
  }

  def withCookies(request: HttpMessage): request.Self = {
    request.withHeaders(cookies:_*)
  }

}
