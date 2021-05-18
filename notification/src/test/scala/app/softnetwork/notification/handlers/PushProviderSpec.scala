package app.softnetwork.notification.handlers

import _root_.akka.actor.typed.ActorSystem
import _root_.akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import org.softnetwork.notification.model.{Platform, BasicDevice, Push}

import app.softnetwork.persistence._

/**
  * Created by smanciot on 28/01/2021.
  */
class PushProviderSpec extends AnyWordSpec with Matchers with MockPushProvider {

  val devices = Seq(
    BasicDevice(
      "fymQwkQ-QYeebUI9-MKvCl:APA91bFQ5MztmvmMs9eblECLp_y11RSgf8pCwW3Lp1GNgghqjFCaCEVxMeRSM8Sjr-q8Vh8-eG1mwjI5MP5pR57x7lMCATwKiWlULFSr2ygO0-OjUsnuNntWEJuFYY0u5UIRKtRQbne-",
      Platform.ANDROID
    )
  )

  implicit def system: ActorSystem[_] = ActorSystem[Nothing](Behaviors.empty, "Push")

  "Push Provider" should {
    "Send Push via FCM" in {
      val ack = send(
        Push.defaultInstance.copy(
          uuid = "test_gcm",
          createdDate = now(),
          lastUpdated = now(),
          devices = devices,
          subject = "Push Test FCM",
          message = "Ceci est un Push test FCM"
        )
      )
      System.err.println(ack)
    }
  }
}
