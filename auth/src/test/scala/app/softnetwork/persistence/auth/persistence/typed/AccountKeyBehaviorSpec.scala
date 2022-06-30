package app.softnetwork.persistence.auth.persistence.typed

import akka.actor.typed.ActorSystem

import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.EntityBehavior

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper

import app.softnetwork.persistence.auth.message._

/**
  * Created by smanciot on 19/04/2020.
  */
class AccountKeyBehaviorSpec extends AnyWordSpecLike with  InMemoryPersistenceTestKit {

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => List(
    AccountKeyBehavior
  )

  import AccountKeyBehavior._

  "AccountKey" must {
    "add key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "add")
      ref ! CommandWrapper(AddAccountKey("account"), probe.ref)
      probe.expectMessage(AccountKeyAdded("add", "account"))
    }

    "remove key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "remove")
      ref ! AddAccountKey("account")
      ref ! CommandWrapper(RemoveAccountKey, probe.ref)
      probe.expectMessage(AccountKeyRemoved("remove"))
    }

    "lookup key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "lookup")
      ref ! AddAccountKey("account")
      ref ! CommandWrapper(LookupAccountKey, probe.ref)
      probe.expectMessage(AccountKeyFound("account"))
      val ref2 = entityRefFor(TypeKey, "empty")
      ref2 ! CommandWrapper(LookupAccountKey, probe.ref)
      probe.expectMessage(AccountKeyNotFound)
    }
  }
}
