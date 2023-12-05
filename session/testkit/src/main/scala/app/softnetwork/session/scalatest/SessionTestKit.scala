package app.softnetwork.session.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.headers.{Cookie, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, HttpMessage, StatusCodes}
import akka.http.scaladsl.testkit.InMemoryPersistenceScalatestRouteTest
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.api.server.config.ServerSettings.RootPath
import app.softnetwork.session.CsrfCheckHeader
import app.softnetwork.session.launch.SessionGuardian
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.SessionConfig
import org.scalatest.Suite

trait SessionTestKit[T <: SessionData with SessionDataDecorator[T]]
    extends InMemoryPersistenceScalatestRouteTest
    with SessionGuardian
    with CsrfCheckHeader { self: Suite with ApiRoutes with SessionMaterials[T] =>

  import app.softnetwork.serialization._

  var httpHeaders: Seq[HttpHeader] = Seq.empty

  implicit def sessionConfig: SessionConfig = SessionConfig.fromConfig(config)

  implicit def companion: SessionDataCompanion[T]

  override implicit lazy val ts: ActorSystem[_] = typedSystem()

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
    val clientHeaders = httpHeaders.flatMap(headerToHeaders)
    var lines = "\n***** Begin Client Headers *****\n"
    for (header <- clientHeaders) {
      lines += s"\t${header.name()}: ${header.value()}\n"
    }
    lines += "***** End Client Headers *****"
    log.info(lines)
    request.withHeaders(request.headers ++ clientHeaders: _*)
  }

  def createSession(
    id: String,
    profile: Option[String] = None,
    admin: Option[Boolean] = None,
    clientId: Option[String] = None,
    anonymous: Boolean = false
  ): Unit = {
    var session = companion.newSession.withId(id).withAnonymous(anonymous)
    if (profile.isDefined) {
      session = session.withProfile(profile.get)
    }
    if (admin.isDefined) {
      session = session.withAdmin(admin.get)
    }
    if (clientId.isDefined) {
      session = session.withClientId(clientId.get)
    }
    createNewSession(session)
  }

  def createNewSession(session: T): Unit = {
    invalidateSession()
    Post(s"/$RootPath/session", CreateSession(session.data)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      httpHeaders = extractHeaders(headers)
    }
  }

  def sessionHeaderName: String

  def refreshableSession: Boolean

  final def extractSession(checkStatus: Boolean = true): Option[T] = {
    withHeaders(Get(s"/$RootPath/session")) ~> routes ~> check {
      if (checkStatus) {
        status shouldEqual StatusCodes.NotFound
      }
      refreshSession(headers)
    }
    val value = httpHeaders.flatMap(headerValue(sessionHeaderName)(_)).headOption
    extractSession(value)
  }

  def refreshSession(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    if (refreshableSession) {
      val updatedHttpHeaders = extractHeaders(headers)
      if (updatedHttpHeaders.exists(existHeader(sessionHeaderName)(_))) {
        httpHeaders = httpHeaders.filterNot(existHeader(sessionHeaderName)(_)) ++ Seq(
          updatedHttpHeaders.find(existHeader(sessionHeaderName)(_))
        ).flatten
      }
    }
    httpHeaders
  }

  def extractSession(value: Option[String]): Option[T] =
    value match {
      case Some(value) => manager.clientSessionManager.decode(value).toOption
      case _           => None
    }

  def sessionExists(): Boolean = {
    httpHeaders.exists(existHeader(sessionHeaderName)(_))
  }

  def invalidateSession(): Unit = {
    if (sessionExists()) {
      withHeaders(Delete(s"/$RootPath/session")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        httpHeaders = extractHeaders(headers)
      }
    }
  }
}
