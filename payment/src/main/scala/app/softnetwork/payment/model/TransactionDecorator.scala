package app.softnetwork.payment.model

trait TransactionDecorator {self: Transaction =>
  lazy val uuid: String = self.id
}
