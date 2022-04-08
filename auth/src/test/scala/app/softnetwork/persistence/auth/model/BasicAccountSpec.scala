package app.softnetwork.persistence.auth.model

import app.softnetwork.security.Sha512Encryption
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import app.softnetwork.persistence.auth.message.SignUp
import Sha512Encryption._

/**
  * Created by smanciot on 18/03/2018.
  */
class BasicAccountSpec extends AnyWordSpec with Matchers {

  private val username = "smanciot"
  private val email = "stephane.manciot@gmail.com"
  private val gsm = "33660010203"
  private val password = "changeit"

  "BasicAccount creation" should {
    "work with username" in {
      val signUp = SignUp(username, password)
      val maybeBasicAccount = BasicAccount(signUp)
      maybeBasicAccount.isDefined shouldBe true
      val baseAccount = maybeBasicAccount.get
      baseAccount.username.isDefined shouldBe true
      baseAccount.username.get shouldBe username
      checkEncryption(baseAccount.credentials, password) shouldBe true
    }
    "work with email" in {
      val signUp = SignUp(email, password)
      val maybeBasicAccount = BasicAccount(signUp)
      maybeBasicAccount.isDefined shouldBe true
      val baseAccount = maybeBasicAccount.get
      baseAccount.email.isDefined shouldBe true
      baseAccount.email.get shouldBe email
      checkEncryption(baseAccount.credentials, password) shouldBe true
    }
    "work with gsm" in {
      val signUp = SignUp(gsm, password)
      val maybeBasicAccount = BasicAccount(signUp)
      maybeBasicAccount.isDefined shouldBe true
      val baseAccount = maybeBasicAccount.get
      baseAccount.gsm.isDefined shouldBe true
      baseAccount.gsm.get shouldBe gsm
      checkEncryption(baseAccount.credentials, password) shouldBe true
    }
  }

}
