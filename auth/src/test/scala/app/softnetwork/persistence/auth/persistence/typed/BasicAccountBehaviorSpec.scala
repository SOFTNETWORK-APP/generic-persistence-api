package app.softnetwork.persistence.auth.persistence.typed

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper
import app.softnetwork.persistence.auth.message._

import MockBasicAccountBehavior._

/**
  * Created by smanciot on 19/04/2020.
  */
class BasicAccountBehaviorSpec extends AnyWordSpecLike with InMemoryPersistenceTestKit {

  private val username = "smanciot"

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "33660010203"

  private val gsm2 = "33660020304"

  private val password = "Changeit1"

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = system => List(
    MockBasicAccountBehavior,
    SchedulerBehavior
  )

  "SignUp" must {
    "fail if confirmed password does not match password" in {
      val probe = createTestProbe[AccountCommandResult]()
      val ref = entityRefFor(TypeKey, "PasswordsNotMatched")
      ref ! CommandWrapper(SignUp(username, password, Some("fake")), probe.ref)
      probe.expectMessage(PasswordsNotMatched)
    }
  }
}
