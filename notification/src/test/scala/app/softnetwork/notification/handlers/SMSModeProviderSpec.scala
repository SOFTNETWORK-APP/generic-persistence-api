package app.softnetwork.notification.handlers

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import org.apache.commons.text.StringEscapeUtils
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.softnetwork.notification.model.{NotificationAck, From, NotificationStatus, SMS}

/**
  * Created by smanciot on 25/08/2018.
  */
class SMSModeProviderSpec extends AnyWordSpec with Matchers with MockSMSProvider {

  var ack: NotificationAck = _

  val gsm = "33612345678"

  val message = "Stéphane,\\\nVous avez gagné 3000 € à la loterie !"

  val sms: SMS = SMS.defaultInstance.withUuid("test").withFrom(From("TEST", None)).withTo(Seq(gsm)).withSubject("SUBJECT")
    .withMessage(StringEscapeUtils.escapeHtml4(message.replaceAll("\\\n", "<br/>")))

  implicit def system: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty[Nothing], "SMS")

  "SMS Provider" should {
    "Send SMS" in {
      ack = send(sms)
      ack.status shouldBe NotificationStatus.Sent
      ack.uuid.isDefined shouldBe true
    }
    "Report reception" in {
      val report = ack(sms.copyWithAck(ack).asInstanceOf[SMS])
      report.results.nonEmpty shouldBe true
      val result = report.results.head
      result.recipient shouldBe gsm
      result.error.isEmpty shouldBe true
    }
  }
}
