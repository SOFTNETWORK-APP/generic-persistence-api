package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.headers.{Cookie, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, StatusCodes}
import akka.http.scaladsl.testkit.InMemoryPersistenceScalatestRouteTest
import app.softnetwork.api.server.config.ServerSettings.RootPath
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

  var httpHeaders: Seq[HttpHeader] = Seq.empty

  private[this] def headerToHeaders: HttpHeader => Seq[HttpHeader] = {
    case cookie: Cookie =>
      cookie.cookies.find(_.name == "XSRF-TOKEN") match {
        case Some(pair) => Seq(cookie, RawHeader("X-XSRF-TOKEN", pair.value))
        case _          => Seq(cookie)
      }
    case raw: RawHeader => Seq(mapRawHeader(raw)).flatten
    case header         => Seq(header)
  }

  def mapRawHeader: RawHeader => Option[RawHeader] = raw => Some(raw)

  def withHeaders(request: HttpMessage): request.Self = {
    request.withHeaders(request.headers ++ httpHeaders.flatMap(headerToHeaders): _*)
  }

  def createSession(
    id: String,
    profile: Option[String] = None,
    admin: Option[Boolean] = None
  ): Unit = {
    invalidateSession()
    Post(s"/$RootPath/session", CreateSession(id, profile, admin)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      httpHeaders = extractHeaders(headers)
    }
  }

  def sessionHeaderName: String

  final def extractSession(): Option[Session] = {
    extractSession(httpHeaders.flatMap(findHeader(sessionHeaderName)(_)).headOption)
  }

  def extractSession(value: Option[String]): Option[Session] =
    value match {
      case Some(value) => sessionManager.clientSessionManager.decode(value).toOption
      case _           => None
    }

  def invalidateSession(): Unit = {
    if (httpHeaders.nonEmpty) {
      withHeaders(Delete(s"/$RootPath/session")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
    }
  }
}
