package app.softnetwork.payment.model

trait AddressDecorator {self: Address =>

  lazy val wrongAddress: Boolean =
    addressLine.trim.isEmpty ||
      city.trim.isEmpty ||
      country.trim.isEmpty ||
      postalCode.trim.isEmpty
}
