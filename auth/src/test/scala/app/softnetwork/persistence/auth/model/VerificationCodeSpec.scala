package app.softnetwork.persistence.auth.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
  * Created by smanciot on 09/04/2018.
  */
class VerificationCodeSpec  extends AnyWordSpec with Matchers {

  "VerificationCode" should {
    "generate pin code of n digits" in {
      (5 to 10) foreach {i =>
        val pin = VerificationCode(i, 5).code
        pin.length shouldEqual i
        pin.matches(s"[0-9]{$i}") shouldBe true
      }
    }
  }
}
