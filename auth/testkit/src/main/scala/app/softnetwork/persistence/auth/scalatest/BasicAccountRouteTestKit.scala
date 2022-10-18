package app.softnetwork.persistence.auth.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, StatusCodes}
import app.softnetwork.api.server.config.Settings.RootPath
import app.softnetwork.persistence.auth.config.Settings.Path
import app.softnetwork.persistence.auth.handlers.MockBasicAccountDao
import app.softnetwork.persistence.auth.message.{AccountCreated, Login, Logout, SignUp}
import app.softnetwork.persistence.auth.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.persistence.auth.service.{AccountService, MockBasicAccountService}
import org.scalatest.Suite

import scala.util.{Failure, Success}

trait BasicAccountRouteTestKit extends AccountRouteTestKit[BasicAccount, BasicAccountProfile] with BasicAccountTestKit  {
  _: Suite =>

  override def accountService: ActorSystem[_] => AccountService = system => MockBasicAccountService(system)

  var cookies: Seq[HttpHeader] = Seq.empty

  import app.softnetwork.serialization._

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
    signOut()
    Post(s"/$RootPath/$Path/signIn", Login(login, password)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      cookies = extractCookies(headers)
    }
  }

  def signOut(): Unit = {
    if(cookies.nonEmpty){
      withCookies(Post(s"/$RootPath/$Path/signOut", Logout)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        cookies = Seq.empty
      }
    }
  }

  def withCookies(request: HttpMessage): request.Self = {
    request.withHeaders(cookies:_*)
  }

}
