package app.softnetwork.persistence.audit

import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

/** Story 13.7 — proves the generic AuditLog assembles the always-present structured fields
  * (`event_type`, `correlation_id`, `service`, `actor`) plus caller fields, resolves `service` from
  * the eventType, and lets a caller override `service` / `actor`. Field rendering is captured via a
  * logback `ListAppender` on the shared audit logger (no LogstashEncoder needed at unit level).
  */
class AuditLogSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private val logger = LoggerFactory.getLogger(AuditLog.LoggerName).asInstanceOf[Logger]
  private val appender = new ListAppender[ILoggingEvent]()

  override def beforeEach(): Unit = {
    appender.list.clear()
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.INFO)
  }

  override def afterEach(): Unit = {
    logger.detachAppender(appender)
    appender.stop()
  }

  private def lastEvent: ILoggingEvent = appender.list.get(appender.list.size() - 1)

  // StructuredArguments.kv("k", v).toString renders as "k=v"; rebuild the field map from the args.
  private def lastArgs: Map[String, String] =
    lastEvent.getArgumentArray
      .map(_.toString)
      .map { s =>
        val i = s.indexOf('=')
        s.substring(0, i) -> s.substring(i + 1)
      }
      .toMap

  "AuditLog (fixed service)" should {
    "emit event_type, correlation_id, service, the default actor and custom fields" in {
      AuditLog("notification").event(
        "cid-1",
        "notification_sent",
        "channel"  -> "email",
        "template" -> "welcome.mustache"
      )
      lastEvent.getMessage shouldBe "audit"
      val m = lastArgs
      m("event_type") shouldBe "notification_sent"
      m("correlation_id") shouldBe "cid-1"
      m("service") shouldBe "notification"
      m("actor") shouldBe AuditLog.DefaultActor
      m("channel") shouldBe "email"
      m("template") shouldBe "welcome.mustache"
    }
  }

  "AuditLog (per-eventType resolver)" should {
    "derive service from the eventType" in {
      val audit =
        AuditLog((et: String) => if (et.startsWith("schedule")) "scheduler" else "licensing")
      audit.event("cid-2", "schedule_fired", "schedule_key" -> "renewal")
      lastArgs("service") shouldBe "scheduler"
      audit.event("cid-3", "license_issued")
      lastArgs("service") shouldBe "licensing"
    }
  }

  "AuditLog override" should {
    "let a caller override service and actor via fields" in {
      AuditLog("licensing").event(
        "cid-4",
        "schedule_fired",
        "service" -> "scheduler",
        "actor"   -> "scheduler"
      )
      val m = lastArgs
      m("service") shouldBe "scheduler"
      m("actor") shouldBe "scheduler"
    }
  }
}
