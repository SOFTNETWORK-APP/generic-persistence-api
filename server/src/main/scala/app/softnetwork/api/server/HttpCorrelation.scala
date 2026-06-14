package app.softnetwork.api.server

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.MDC
import sttp.tapir.{extractFromRequest, EndpointInput}
import sttp.tapir.model.ServerRequest

import java.util.UUID

/** Story 13.7 Phase B (gate #1) — cross-service correlation id ingress.
  *
  * One id per inbound HTTP request, extracted from `X-Correlation-Id` or generated when the client
  * does not supply one, echoed on the response, and propagated two ways:
  *   - as MDC (`correlation_id`) for the SYNCHRONOUS akka-http request thread's operational stdout
  *     logs. MDC is a `ThreadLocal` and does NOT survive an async (`Future`) boundary, so it is NOT
  *     the carrier across the event-sourced path (C2) nor into a tapir `serverLogic` `Future`
  *     (C14);
  *   - as DATA into tapir handlers via [[correlationInput]]: `serverLogic` receives the id as a
  *     value and threads it onto the command (`cmd.withCorrelationId(cid)`, an
  *     [[app.softnetwork.persistence.message.AuditableCommand]]) on the request thread. The command
  *     carries it across the sharding boundary (Kryo); the handler stamps it onto the journaled
  *     event's proto field ([[app.softnetwork.persistence.message.AuditableEvent]]) — the durable
  *     hop that survives replay.
  *
  * [[withCorrelation]] re-stamps the canonical id onto the inbound request before the inner route
  * runs, so any downstream tapir endpoint reading [[correlationInput]] observes the SAME id the
  * directive generated/echoed — the response header and the value threaded into the command never
  * diverge, even when the client sent no header.
  *
  * Mirrors [[HttpMetrics.withMetrics]]; apply at the same `ApiRoutes.mainRoutes` attach point:
  * {{{
  * HttpMetrics.withMetrics { HttpCorrelation.withCorrelation { ... }}
  * }}}
  */
object HttpCorrelation {

  /** Inbound + echoed HTTP header name. */
  final val HeaderName = "X-Correlation-Id"

  /** Lower-cased header name for akka-http `HttpHeader.is` matching (idempotent echo check). */
  private final val HeaderNameLower = HeaderName.toLowerCase

  /** SLF4J MDC key — must match `<includeMdcKeyName>correlation_id</includeMdcKeyName>` in the
    * services' `logback.xml`.
    */
  final val MdcKey = "correlation_id"

  /** Extract a non-blank id, or generate a fresh one. The single source of the default, shared by
    * the directive and [[correlationInput]] so a generated id is identical wherever it is read.
    */
  private def orGenerate(value: Option[String]): String =
    value.map(_.trim).filter(_.nonEmpty).getOrElse(UUID.randomUUID().toString)

  /** akka-http directive — extract-or-generate the id, expose it on MDC for synchronous-route
    * stdout logs, re-stamp the canonical header onto the request (so downstream tapir
    * [[correlationInput]] sees it), and echo it on the response.
    *
    * Re-entrant: it does not clobber a `correlation_id` already on MDC (an outer application set
    * it), and it echoes the header only if the response does not already carry one — so it is safe
    * to apply at BOTH `ApiRoutes.mainRoutes` and `Endpoint.endpointsToRoute` (a tapir endpoint set
    * is thus self-sufficient even when mounted outside `mainRoutes`, while nesting under
    * `mainRoutes` produces a single header and one MDC scope).
    */
  def withCorrelation(inner: Route): Route =
    optionalHeaderValueByName(HeaderName) { hdr =>
      val cid = orGenerate(hdr)
      // own the MDC scope only if no outer application already set it (avoid clobbering/early-clear).
      val ownsMdc = MDC.get(MdcKey) == null
      if (ownsMdc) MDC.put(MdcKey, cid)
      val stamp: HttpRequest => HttpRequest =
        req => req.removeHeader(HeaderName).addHeader(RawHeader(HeaderName, cid))
      mapRequest(stamp) {
        mapResponse { resp =>
          // best-effort cleanup for the synchronous path; the async hazard is why the durable
          // carrier is the explicit data input below, not this ThreadLocal.
          if (ownsMdc) MDC.remove(MdcKey)
          if (resp.headers.exists(_.is(HeaderNameLower))) resp
          else resp.addHeader(RawHeader(HeaderName, cid))
        }(inner)
      }
    }

  /** Tapir input — yields the correlation id as a **value** so `serverLogic` threads it onto the
    * command on the request thread, never via an MDC read across the `Future` (C14). It reads the
    * (directive-stamped) `X-Correlation-Id` and, if absent, generates a default via [[orGenerate]]
    * — so `serverLogic` ALWAYS receives a non-empty id, even when no client header arrived and even
    * if the endpoint were mounted without [[withCorrelation]]. Prepend to an endpoint's inputs:
    * {{{
    * endpoint.in(HttpCorrelation.correlationInput).in(...).serverLogic { case (cid, in) =>
    *   val cmd = SomeCommand(...)
    *   cmd.withCorrelationId(cid)
    *   run(cmd)
    * }
    * }}}
    */
  val correlationInput: EndpointInput[String] =
    extractFromRequest((req: ServerRequest) => orGenerate(req.header(HeaderName)))
}
