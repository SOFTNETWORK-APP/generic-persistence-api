package akka.http.scaladsl.testkit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{Cookie, HttpCookiePair, RawHeader}
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import app.softnetwork.api.server.{ApiRoutes, ApiServer}
import app.softnetwork.config.Settings
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.persistence.scalatest.{InMemoryPersistenceTestKit, PersistenceTestKit}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.scalatest.exceptions.TestFailedException
import org.scalatest.Suite

/**
  * Created by smanciot on 24/04/2020.
  *
  */
trait PersistenceScalatestRouteTest extends ApiServer
  with PersistenceTestKit
  with RouteTest
  with TestFrameworkInterface
  with ScalatestUtils
  with Json4sSupport { this: Suite with ApiRoutes with SchemaProvider =>

  override protected def createActorSystem(): ActorSystem = {
    import app.softnetwork.persistence.typed._
    typedSystem()
  }

  override lazy val interface: String = hostname

  override lazy val port: Int = {
    import java.net.ServerSocket
    new ServerSocket(0).getLocalPort
  }

  implicit lazy val timeout: RouteTestTimeout = RouteTestTimeout(Settings.DefaultTimeout)

  def failTest(msg: String) = throw new TestFailedException(msg, 11)

  def testExceptionHandler: ExceptionHandler = ExceptionHandler(new PartialFunction[Throwable, Route] {
    override def isDefinedAt(t: Throwable): Boolean = true

    override def apply(t: Throwable): Route = RouteDirectives.failWith(t)
  })

  lazy val routes: Route = mainRoutes(typedSystem())

  def extractCookies(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    headers.filter(header => {
      val name = header.lowercaseName()
      log.info(s"$name:${header.value}")
      name == "set-cookie"
    }).flatMap(header => {
      val cookie = header.value().split("=")
      val name = cookie.head
      val value = cookie.tail.mkString("").split(";").head
      var ret: Seq[HttpHeader] = Seq(Cookie(name, value))
      if(name == "XSRF-TOKEN")
        ret = ret ++ Seq(RawHeader("X-XSRF-TOKEN", value))
      ret
    })
  }

  def findCookie(name: String): HttpHeader => Option[HttpCookiePair] = {
    case Cookie(cookies) => cookies.find(_.name == name)
    case _               => None
  }

}

trait InMemoryPersistenceScalatestRouteTest extends PersistenceScalatestRouteTest with InMemoryPersistenceTestKit {
  _: Suite with ApiRoutes =>
}