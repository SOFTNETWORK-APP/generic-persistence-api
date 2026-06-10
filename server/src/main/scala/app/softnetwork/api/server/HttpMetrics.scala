package app.softnetwork.api.server

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

  /** Collapse id-like segments (UUID/hex >= 8 chars, or all-digits) to `:id`. */
  def normalizePath(path: String): String =
    path
      .split("/", -1)
      .map { seg =>
        if (seg.isEmpty) seg
        else if (seg.length >= 8 && seg.matches("[0-9a-fA-F-]+")) ":id"
        else if (seg.matches("[0-9]+")) ":id"
        else seg
      }
      .mkString("/")
}
