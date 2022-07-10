package app.softnetwork.persistence.auth.persistence.typed

import akka.actor.testkit.typed.scaladsl.TestProbe
import app.softnetwork.kv.message._
import app.softnetwork.persistence.auth.scalatest.BasicAccountTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper

/**
  * Created by smanciot on 19/04/2020.
  */
class AccountKeyBehaviorSpec extends AnyWordSpecLike with BasicAccountTestKit {

  import AccountKeyBehavior._

  val probe: TestProbe[KvCommandResult] = createTestProbe[KvCommandResult]()

  "AccountKey" must {
    "add key" in {
      val ref = entityRefFor(TypeKey, "add")
      ref ! CommandWrapper(Put("account"), probe.ref)
      probe.expectMessageType[KvAdded.type]
    }

    "remove key" in {
      val ref = entityRefFor(TypeKey, "remove")
      ref ! Put("account")
      ref ! CommandWrapper(Remove, probe.ref)
      probe.expectMessageType[KvRemoved.type]
    }

    "lookup key" in {
      val ref = entityRefFor(TypeKey, "lookup")
      ref ! Put("account")
      ref ! CommandWrapper(Lookup, probe.ref)
      probe.expectMessage(KvFound("account"))
      val ref2 = entityRefFor(TypeKey, "empty")
      ref2 ! CommandWrapper(Lookup, probe.ref)
      probe.expectMessageType[KvNotFound.type]
    }
  }
}
