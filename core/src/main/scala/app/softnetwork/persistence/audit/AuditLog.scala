package app.softnetwork.persistence.audit

import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

/** Story 13.7 — generic structured audit logger (cross-service audit trail).
  *
  * Every service emits to ONE fixed audit logger name ([[AuditLog.LoggerName]]) so the **shared**
  * `logback.xml` template needs a single `additivity=false` route to the synchronous, non-dropping
  * `audit.log` appender. Each pod still writes its own pod-local `audit.log` (separate files
  * because separate pods); its promtail sidecar tails it → Loki `{stream="audit"}`. The logical
  * domain is carried by the **`service` JSON field** (not the logger name, not the file), so
  * licensing / notification / payment / scheduler lines are distinguished at query time while
  * sharing one route.
  *
  * `correlationId` is passed **explicitly** (threaded from the command/event as plain data) and
  * emitted as a structured `correlation_id` field — it is deliberately NOT read from MDC, because
  * the projection streams emit audit lines from `Future` continuations where a `ThreadLocal` MDC
  * value would not survive (Story 13.7 C2). MDC is reserved for the synchronous HTTP request thread
  * (see `app.softnetwork.api.server.HttpCorrelation`).
  *
  * PII / secret masking (emails, `sk_`/`pk_`/`whsec_`/`Bearer` tokens) is enforced downstream at
  * the logback `MaskingJsonGeneratorDecorator`, so callers never need to pre-redact; code must
  * still never pass a raw secret to a logger (defence in depth).
  *
  * @param serviceOf
  *   maps an `eventType` to its logical business domain (the `service` field). A single-domain pod
  *   passes a constant (see [[AuditLog.apply(service:String)*]]); a pod that emits lines for
  *   several domains (e.g. the licensing pod) passes a resolver. A caller may still override per
  *   call by passing an explicit `"service"` entry in `fields`.
  * @param defaultActor
  *   the `actor` for machine-initiated flows when a caller does not override it.
  */
class AuditLog(
  serviceOf: String => String,
  defaultActor: String = AuditLog.DefaultActor
) {

  private val log = LoggerFactory.getLogger(AuditLog.LoggerName)

  /** Emit an audit event.
    *
    * Always emits `event_type`, `correlation_id`, `service` (from [[serviceOf]]) and `actor`
    * (default [[defaultActor]]); `service` / `actor` can be overridden by passing a `"service"` /
    * `"actor"` entry in `fields`.
    *
    * @param correlationId
    *   the cross-service correlation id (threaded as data; `"-"` when unknown)
    * @param eventType
    *   the catalog event type, e.g. `license_issued`, `notification_sent`
    * @param fields
    *   additional structured fields (`organization_id`, `channel`, `template`, …); values are
    *   emitted verbatim and masked downstream by the encoder
    */
  def event(correlationId: String, eventType: String, fields: (String, Any)*): Unit = {
    val provided = fields.iterator.map(_._1).toSet
    val base = List.newBuilder[(String, Any)]
    base += "event_type"                                 -> eventType
    base += "correlation_id"                             -> correlationId
    if (!provided.contains("service")) base += "service" -> serviceOf(eventType)
    if (!provided.contains("actor")) base += "actor"     -> defaultActor
    val args: Seq[AnyRef] = (base.result() ++ fields).map { case (k, v) => kv(k, v) }
    // The "audit" message has no `{}` placeholders on purpose — logstash-logback-encoder still
    // appends every StructuredArgument as a top-level JSON field. Binds slf4j info(String, Object...).
    log.info("audit", args: _*)
  }
}

object AuditLog {

  /** The single fixed audit logger name — one `additivity=false` route in the shared `logback.xml`
    * template feeds every service's pod-local `audit.log`; the `service` field distinguishes
    * domains.
    */
  final val LoggerName = "app.softnetwork.audit"

  /** Default `actor` for machine-initiated flows. */
  final val DefaultActor = "system"

  /** Audit logger for a single-domain pod (e.g. notification) — every line carries `service`. */
  def apply(service: String): AuditLog =
    new AuditLog(_ => service)

  /** Audit logger for a pod that emits lines for several domains (e.g. the licensing pod) —
    * `service` is resolved per `eventType`.
    */
  def apply(serviceOf: String => String): AuditLog =
    new AuditLog(serviceOf)
}
