package app.softnetwork.api.server

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.MDC
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

/** Story 13.7 Phase B (gate #1) — proves the HttpCorrelation ingress: extract-or-generate the
  * `X-Correlation-Id`, echo it on the response, re-inject the canonical id onto the request so a
  * downstream consumer (akka-http directive AND tapir `correlationInput`) reads the SAME value, and
  * expose it on MDC for the synchronous request thread.
  */
class HttpCorrelationSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with Directives {

  import HttpCorrelation._

  private val UuidRe =
    "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$".r

  // A directive-wrapped route whose leaves report what they observe PER REQUEST (extractRequest /
  // optionalHeaderValueByName defer evaluation to request time, so the directive's mapRequest +
  // MDC.put have already run).
  private val route: Route =
    withCorrelation {
      concat(
        path("ping")(get(complete("pong"))),
        // what a downstream route reads off the (re-injected) request header
        path("seen")(get(optionalHeaderValueByName(HeaderName) { h =>
          val seen: String = h.getOrElse("none")
          complete(seen)
        })),
        // what MDC holds on the synchronous request thread
        path("mdc")(get(extractRequest { _ =>
          val mdc: String = Option(MDC.get(MdcKey)).getOrElse("none")
          complete(mdc)
        }))
      )
    }

  // End-to-end tapir proof (C14): correlationInput delivers the id to serverLogic as DATA. Mounted
  // via Endpoint.endpointsToRoute (which wraps with withCorrelation) and with NO outer directive — so
  // it proves the endpoint set is self-sufficient (default generation + echo) on its own.
  private val tapirRoute: Route = {
    implicit val ec: ExecutionContext = system.dispatcher
    val echo: ServerEndpoint[Any, Future] =
      endpoint.get
        .in("echo")
        .in(correlationInput)
        .out(stringBody)
        .serverLogic[Future](cid => Future.successful(Right(cid)))
    Endpoint.endpointsToRoute(List(echo))
  }

  "HttpCorrelation.withCorrelation" should {
    "generate and echo an X-Correlation-Id when the client sends none" in {
      Get("/ping") ~> route ~> check {
        val cid = header(HeaderName).map(_.value)
        cid shouldBe defined
        cid.get should fullyMatch regex UuidRe
        responseAs[String] shouldBe "pong"
      }
    }

    "echo a client-supplied X-Correlation-Id unchanged" in {
      Get("/ping") ~> addHeader(RawHeader(HeaderName, "abc-123")) ~> route ~> check {
        header(HeaderName).map(_.value) shouldBe Some("abc-123")
      }
    }

    "treat a blank client header as absent and generate a fresh id" in {
      Get("/ping") ~> addHeader(RawHeader(HeaderName, "   ")) ~> route ~> check {
        val cid = header(HeaderName).map(_.value)
        cid shouldBe defined
        cid.get should fullyMatch regex UuidRe
      }
    }

    "re-inject the id so a downstream route reads the SAME value echoed on the response" in {
      Get("/seen") ~> route ~> check {
        val downstream = responseAs[String]
        downstream should not be "none"
        header(HeaderName).map(_.value) shouldBe Some(downstream)
      }
    }

    "expose the id on MDC for the synchronous request thread" in {
      Get("/mdc") ~> addHeader(RawHeader(HeaderName, "mdc-1")) ~> route ~> check {
        responseAs[String] shouldBe "mdc-1"
      }
    }

    "be re-entrant: nesting produces a single echoed header" in {
      val nested = withCorrelation(withCorrelation(path("ping")(get(complete("pong")))))
      Get("/ping") ~> nested ~> check {
        headers.count(_.is(HeaderName.toLowerCase)) shouldBe 1
        responseAs[String] shouldBe "pong"
      }
    }
  }

  "HttpCorrelation.correlationInput (self-sufficient via Endpoint.endpointsToRoute)" should {
    "generate a default id, deliver it to serverLogic AND echo it — with no outer directive" in {
      Get("/echo") ~> tapirRoute ~> check {
        val delivered = responseAs[String]
        delivered should fullyMatch regex UuidRe
        header(HeaderName).map(_.value) shouldBe Some(delivered)
      }
    }

    "deliver a client-supplied id to serverLogic and echo it unchanged" in {
      Get("/echo") ~> addHeader(RawHeader(HeaderName, "tapir-9")) ~> tapirRoute ~> check {
        responseAs[String] shouldBe "tapir-9"
        header(HeaderName).map(_.value) shouldBe Some("tapir-9")
      }
    }
  }
}
