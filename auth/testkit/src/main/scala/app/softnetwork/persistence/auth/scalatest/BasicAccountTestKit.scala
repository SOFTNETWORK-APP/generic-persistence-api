package app.softnetwork.persistence.auth.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.auth.handlers.MockBasicAccountHandler
import app.softnetwork.persistence.auth.model.{BasicAccount, BasicAccountProfile}
import app.softnetwork.persistence.auth.persistence.query.AccountEventProcessorStreams.InternalAccountEvents2AccountProcessorStream
import app.softnetwork.persistence.auth.persistence.typed.{AccountBehavior, MockBasicAccountBehavior}
import app.softnetwork.persistence.query.InMemoryJournalProvider
import org.scalatest.Suite

trait BasicAccountTestKit extends AccountTestKit[BasicAccount, BasicAccountProfile] {_: Suite =>
  override def accountBehavior: ActorSystem[_] => AccountBehavior[BasicAccount, BasicAccountProfile] = _ =>
    MockBasicAccountBehavior

  override def internalAccountEvents2AccountProcessorStream: ActorSystem[_] =>
    InternalAccountEvents2AccountProcessorStream = sys =>
    new InternalAccountEvents2AccountProcessorStream with MockBasicAccountHandler with InMemoryJournalProvider {
      override def tag: String = s"${MockBasicAccountBehavior.persistenceId}-to-internal"
      override lazy val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
    }
}

