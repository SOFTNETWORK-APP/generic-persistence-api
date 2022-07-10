package app.softnetwork.persistence.auth.persistence.typed

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper
import app.softnetwork.persistence.auth.message._
import MockBasicAccountBehavior._
import app.softnetwork.persistence.auth.scalatest.BasicAccountTestKit

/**
  * Created by smanciot on 19/04/2020.
  */
class BasicAccountBehaviorSpec extends AnyWordSpecLike with BasicAccountTestKit {

  private val username = "smanciot"

  private val password = "Changeit1"

  "SignUp" must {
    "fail if confirmed password does not match password" in {
      val probe = createTestProbe[AccountCommandResult]()
      val ref = entityRefFor(TypeKey, "PasswordsNotMatched")
      ref ! CommandWrapper(SignUp(username, password, Some("fake")), probe.ref)
      probe.expectMessage(PasswordsNotMatched)
    }
  }
}
