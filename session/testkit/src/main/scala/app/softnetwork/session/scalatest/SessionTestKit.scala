package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.{HttpHeader, HttpMessage, StatusCodes}
import akka.http.scaladsl.testkit.InMemoryPersistenceScalatestRouteTest
import app.softnetwork.api.server.config.ServerSettings.RootPath
import app.softnetwork.session.config.Settings
import app.softnetwork.session.launch.SessionGuardian
import app.softnetwork.session.model.SessionCompanion
import org.scalatest.Suite
import org.softnetwork.session.model.Session

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
        cookies = extractCookies(headers)
      }
    }
  }
}

trait RefreshableCookieSessionTestKit
    extends SessionTestKit
    with RefreshableCookieSessionServiceRoutes { _: Suite => }

trait OneOffCookieSessionTestKit extends SessionTestKit with OneOffCookieSessionServiceRoutes {
  _: Suite =>
}
