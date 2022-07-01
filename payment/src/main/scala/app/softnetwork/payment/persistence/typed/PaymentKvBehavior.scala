package app.softnetwork.payment.persistence.typed

import app.softnetwork.kv.persistence.typed.KeyValueBehavior

trait PaymentKvBehavior extends KeyValueBehavior {
  override def persistenceId: String = "PaymentKeys"
}

object PaymentKvBehavior extends PaymentKvBehavior
