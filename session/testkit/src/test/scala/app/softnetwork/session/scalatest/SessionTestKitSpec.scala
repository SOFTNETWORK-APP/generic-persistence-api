package app.softnetwork.session.scalatest

import org.scalatest.wordspec.AnyWordSpecLike

class SessionTestKitSpec extends AnyWordSpecLike with SessionTestKit {

  val id: String = "id"

  "Session" must {
    "be created" in {
      createSession(id)
    }
    "be invalidated" in {
      invalidateSession()
    }
  }
}
