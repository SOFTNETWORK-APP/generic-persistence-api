package app.softnetwork.api.server

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayOutputStream

/** Story 13.6 Phase B — proves the HttpMetrics directive emits request/latency samples into the
  * default registry for normal, rejection and exception responses, and that `normalizePath`
  * collapses id-like segments.
  */
class HttpMetricsSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with Directives {

  // Mirror the ApiRoutes wrapping: metrics OUTSIDE rejection/exception handling so the final response
  // (including the rejection-derived 404 and the exception-derived 500) is observed.
  private val exceptionHandler = ExceptionHandler { case _: RuntimeException =>
    complete(StatusCodes.InternalServerError -> "boom")
  }

  private val route: Route =
    HttpMetrics.withMetrics {
      handleRejections(RejectionHandler.default) {
        handleExceptions(exceptionHandler) {
          concat(
            path("ping")(get(complete("pong"))),
            path("licenses" / Segment)(id => get(complete(id))),
            path("boom")(get(failWith(new RuntimeException("boom"))))
          )
        }
      }
    }

  private def scrapeText(): String = {
    val writer = PrometheusTextFormatWriter.builder().build()
    val out = new ByteArrayOutputStream()
    writer.write(out, PrometheusRegistry.defaultRegistry.scrape())
    out.toString("UTF-8")
  }

  "HttpMetrics.normalizePath" should {
    "collapse numeric and uuid/hex segments to :id" in {
      HttpMetrics.normalizePath("/api/licenses/123") shouldBe "/api/licenses/:id"
      HttpMetrics.normalizePath(
        "/api/licenses/550e8400-e29b-41d4-a716-446655440000"
      ) shouldBe "/api/licenses/:id"
    }
    "leave non-id segments untouched" in {
      HttpMetrics.normalizePath("/api/healthcheck") shouldBe "/api/healthcheck"
      HttpMetrics.normalizePath("/ping") shouldBe "/ping"
    }
  }

  "The HttpMetrics directive" should {
    "record a 200, normalising an id segment in the path label" in {
      Get("/ping") ~> route ~> check { status shouldBe StatusCodes.OK }
      Get("/licenses/550e8400-e29b-41d4-a716-446655440000") ~> route ~> check {
        status shouldBe StatusCodes.OK
      }
      val text = scrapeText()
      text should include("""http_requests_total{method="GET",path="/ping",status="200"}""")
      text should include("""http_requests_total{method="GET",path="/licenses/:id",status="200"}""")
      // histogram observed too
      text should include("""http_request_duration_seconds_count{method="GET",path="/ping"}""")
    }

    "record a rejection-derived 404 response" in {
      Get("/does-not-exist") ~> route ~> check { status shouldBe StatusCodes.NotFound }
      scrapeText() should include(
        """http_requests_total{method="GET",path="/does-not-exist",status="404"}"""
      )
    }

    "record an exception-derived 500 response" in {
      Get("/boom") ~> route ~> check { status shouldBe StatusCodes.InternalServerError }
      scrapeText() should include("""http_requests_total{method="GET",path="/boom",status="500"}""")
    }
  }
}
