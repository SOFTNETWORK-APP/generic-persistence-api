package app.softnetwork.api.server

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.prometheus.metrics.core.metrics.{Counter, Histogram}

/** Story 13.6 Phase B — HTTP request rate + latency, recorded into the global
  * `PrometheusRegistry.defaultRegistry`. A downstream service's `/metrics` endpoint (served from
  * the same default registry) exposes these series; the `service` label is added at scrape time by
  * the ServiceMonitor relabeling (these are library-defined series with a fixed label set).
  *
  * `path` is normalised (id-like segments collapsed to `:id`) to bound cardinality, since the raw
  * request path can embed UUIDs / numeric ids.
  */
object HttpMetrics {

  private val requests: Counter = Counter
    .builder()
    .name("http_requests")
    .help("HTTP requests, by method / normalised path / status")
    .labelNames("method", "path", "status")
    .register()

  private val duration: Histogram = Histogram
    .builder()
    .name("http_request_duration_seconds")
    .help("HTTP request duration in seconds, by method / normalised path")
    .labelNames("method", "path")
    .classicUpperBounds(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
    .register()

  def record(method: String, path: String, status: Int, seconds: Double): Unit = {
    val p = normalizePath(path)
    requests.labelValues(method, p, status.toString).inc()
    duration.labelValues(method, p).observe(seconds)
  }

  /** akka-http directive: times the request and records method / normalised-path / status + latency
    * when the inner route completes. Wrap it OUTSIDE rejection/exception handling so `mapResponse`
    * observes the FINAL response (rejection- and exception-derived responses included).
    */
  def withMetrics(inner: Route): Route =
    extractRequest { req =>
      val startNanos = System.nanoTime()
      mapResponse { resp =>
        record(
          req.method.value,
          req.uri.path.toString,
          resp.status.intValue(),
          (System.nanoTime() - startNanos) / 1e9d
        )
        resp
      }(inner)
    }

  private val HexLike = "^[0-9a-fA-F-]+$".r
  private val DigitsOnly = "^[0-9]+$".r

  /** Collapse id-like segments (UUID/hex >= 8 chars, or all-digits) to `:id`. */
  def normalizePath(path: String): String =
    path
      .split("/", -1)
      .map { seg =>
        if (seg.isEmpty) seg
        else if (seg.length >= 8 && HexLike.pattern.matcher(seg).matches()) ":id"
        else if (DigitsOnly.pattern.matcher(seg).matches()) ":id"
        else seg
      }
      .mkString("/")
}
