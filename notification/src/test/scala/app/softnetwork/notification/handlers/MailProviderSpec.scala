package app.softnetwork.notification.handlers

import java.io.{FileOutputStream, File}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import app.softnetwork.notification.config.Settings
import org.softnetwork.notification.model._

/**
  * Created by smanciot on 25/08/2018.
  */
class MailProviderSpec extends AnyFlatSpec with Matchers with MockMailProvider {

  var ack: NotificationAck = _

  val email = "stephane.manciot@gmail.com"

  val message = "Stéphane,\\\nVous avez gagné 3000 € à la loterie !"

  val mail: Mail = Mail.defaultInstance
    .withUuid("test")
    .withFrom(From.defaultInstance.withValue(Settings.Config.mail.username))
    .withTo(Seq(email))
    .withSubject("SUBJECT")
    .withMessage(message)

  implicit def system: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "Mail")

  "Mail Provider" should "send mail" in{
    ack = send(mail)
    ack.status shouldBe NotificationStatus.Sent
  }

  "Mail Provider" should "send multipart mail" in{
    val path = "/tmp/attachment.txt"
    val attachment = new File(path)
    attachment.createNewFile()
    val os = new FileOutputStream(attachment)
    os.write("ceci est un test !".getBytes())
    os.close()
    ack = send(mail.withAttachment(
      Attachment(
        "mon attachement",
        path,
        Some("la description de mon attachement")
      )
    ))
    ack.status shouldBe NotificationStatus.Sent
  }
}
