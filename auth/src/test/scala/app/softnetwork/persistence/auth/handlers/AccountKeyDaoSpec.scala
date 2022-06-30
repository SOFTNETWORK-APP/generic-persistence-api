package app.softnetwork.persistence.auth.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.EntityBehavior

import org.scalatest.wordspec.AnyWordSpecLike

import app.softnetwork.persistence.auth.persistence.typed._

/**
  * Created by smanciot on 19/04/2020.
  */
class AccountKeyDaoSpec extends AccountKeyDao with AccountKeyHandler with AnyWordSpecLike with InMemoryPersistenceTestKit {

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => List(
    AccountKeyBehavior
  )

  implicit lazy val system: ActorSystem[Nothing] = typedSystem()

  "AccountKey" must {
    "add key" in {
      addAccountKey("add", "account")
      lookupAccount("add") await {
        case Some(account) => account shouldBe "account"
        case _             => fail()
      }
    }

    "remove key" in {
      addAccountKey("remove", "account")
      removeAccountKey("remove")
      lookupAccount("remove") await {
        case Some(_) => fail()
        case _             => succeed
      }
    }
  }

}
