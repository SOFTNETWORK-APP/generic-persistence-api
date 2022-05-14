package app.softnetwork.payment.model

trait PaymentUserDecorator{_: PaymentUser =>
    lazy val externalUuid: String = customerUuid.getOrElse(vendorUuid.getOrElse(sellerUuid.getOrElse("undefined")))
    lazy val customerUuid: Option[String] = account.customer
    lazy val vendorUuid: Option[String] = account.vendor
    lazy val sellerUuid: Option[String] = account.seller
}
