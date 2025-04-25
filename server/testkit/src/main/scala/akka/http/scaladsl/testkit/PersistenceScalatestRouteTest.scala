package akka.http.scaladsl.testkit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{Cookie, HttpCookiePair, RawHeader, `Set-Cookie`}
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.{Materializer, SystemMaterializer}
import app.softnetwork.api.server.scalatest.ServerTestKit
import app.softnetwork.api.server.{ApiRoutes, ApiServer}
import app.softnetwork.config.Settings
import app.softnetwork.persistence.scalatest.{InMemoryPersistenceTestKit, PersistenceTestKit}
import app.softnetwork.persistence.schema.Schema
import app.softnetwork.persistence.typed._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.scalatest.exceptions.TestFailedException
import org.scalatest.Suite

import scala.concurrent.ExecutionContextExecutor

/** Created by smanciot on 24/04/2020.
  */
trait PersistenceScalatestRouteTest
    extends ApiServer
    with ServerTestKit
    with PersistenceTestKit
    with RouteTest
    with TestFrameworkInterface
    with ScalatestUtils
    with Json4sSupport { this: Suite with ApiRoutes with Schema =>

  override protected def createActorSystem(): ActorSystem = {
    typedSystem()
  }

  override implicit lazy val system: ActorSystem = createActorSystem()

  override implicit lazy val executor: ExecutionContextExecutor = system.dispatcher

  override implicit lazy val materializer: Materializer = SystemMaterializer(system).materializer

  implicit lazy val timeout: RouteTestTimeout = RouteTestTimeout(Settings.DefaultTimeout)

  def failTest(msg: String) = throw new TestFailedException(msg, 11)

  def testExceptionHandler: ExceptionHandler = ExceptionHandler(
    new PartialFunction[Throwable, Route] {
      override def isDefinedAt(t: Throwable): Boolean = true

      override def apply(t: Throwable): Route = RouteDirectives.failWith(t)
    }
  )

  lazy val routes: Route = mainRoutes(typedSystem())

  @deprecated(
    "this method has been replaced by extractHeaders and will be removed",
    since = "0.3.1.1"
  )
  def extractCookies(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    headers
      .filter(header => {
        val name = header.lowercaseName()
        log.info(s"$name:${header.value}")
        name == "set-cookie"
      })
      .flatMap(header => {
        val cookie = header.value().split("=")
        val name = cookie.head
        val value = cookie.tail.mkString("=").split(";").head
        if (value == "deleted") {
          Seq.empty
        } else {
          Seq(Cookie(name, value))
        }
      })
  }

  @deprecated("this method has been replaced by findHeader and will be removed", since = "0.3.1.1")
  def findCookie(name: String): HttpHeader => Option[HttpCookiePair] = {
    case Cookie(cookies) => cookies.find(_.name == name)
    case _               => None
  }

  def extractHeaders(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    var lines = "\n***** Begin Server Headers *****\n"
    val ret = headers
      .flatMap {
        case header: `Set-Cookie` =>
          val cookie = header.value().split("=")
          val name = cookie.head
          val value = cookie.tail.mkString("=").split(";").head
          lines += s"\t${header.name()}: ${header.value()}\n"
          if (value == "deleted") {
            Seq.empty
          } else {
            Seq(Cookie(name, value))
          }
        case header: RawHeader =>
          val name = header.name
          val value = header.value
          lines += s"\t$name: $value\n"
          if (value.isEmpty) {
            Seq.empty
          } else {
            Seq(RawHeader(name, value))
          }
        case _ => Seq.empty
      }
    lines += "***** End Server Headers *****"
    log.info(lines)
    ret
  }

  def headerValue(name: String): HttpHeader => Option[String] = {
    case Cookie(cookies)                => cookies.find(_.name == name).map(_.value)
    case r: RawHeader if r.name == name => Some(r.value)
    case _                              => None
  }

  def findHeader(name: String): HttpHeader => Option[HttpHeader] = {
    case c: Cookie if c.cookies.exists(_.name == name) => Some(c)
    case other if other.name() == name                 => Some(other)
    case _                                             => None
  }

  def existHeader(name: String): HttpHeader => Boolean = header =>
    findHeader(name)(header).isDefined
}

trait InMemoryPersistenceScalatestRouteTest
    extends PersistenceScalatestRouteTest
    with InMemoryPersistenceTestKit {
  _: Suite with ApiRoutes =>
}
