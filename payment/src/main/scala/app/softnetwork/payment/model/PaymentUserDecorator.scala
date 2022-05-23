package app.softnetwork.payment.model

trait PaymentUserDecorator{self: PaymentUser =>
  lazy val view: PaymentUserView = PaymentUserView(self)
}

case class PaymentUserView(firstName: String,
                           lastName: String,
                           email: String,
                           nationality: String,
                           birthday: String,
                           countryOfResidence: String,
                           userId: Option[String],
                           walletId: Option[String],
                           externalUuid: String)

object PaymentUserView{
  def apply(paymentUser: PaymentUser): PaymentUserView = {
    import paymentUser._
    PaymentUserView(firstName, lastName, email, nationality, birthday, countryOfResidence, userId, walletId, externalUuid)
  }
}
