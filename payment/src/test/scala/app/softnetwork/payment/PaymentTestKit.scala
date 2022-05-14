package app.softnetwork.payment

import akka.actor.typed.ActorSystem
import app.softnetwork.payment.persistence.typed.{MockPaymentAccountBehavior, MockTransactionBehavior}
import app.softnetwork.persistence.auth.persistence.typed.AccountKeyBehavior
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.{EntityBehavior, Singleton}
import org.scalatest.Suite

trait PaymentTestKit extends InMemoryPersistenceTestKit {_: Suite =>

  implicit lazy val tsystem: ActorSystem[_] = typedSystem()

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    AccountKeyBehavior,
    MockPaymentAccountBehavior,
    MockTransactionBehavior
  )

  /**
    *
    * initialize all singletons
    */
  override def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq.empty

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys => Seq.empty

}
