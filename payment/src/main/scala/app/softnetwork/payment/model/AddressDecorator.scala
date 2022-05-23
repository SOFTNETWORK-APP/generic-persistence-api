package app.softnetwork.payment.model

trait AddressDecorator {self: Address =>

  lazy val wrongAddress: Boolean =
    addressLine.trim.isEmpty ||
      city.trim.isEmpty ||
      country.trim.isEmpty ||
      postalCode.trim.isEmpty

  lazy val view: AddressView = AddressView(self)
}

case class AddressView(addressLine: String,
                       city: String,
                       postalCode: String,
                       country: String)

object AddressView {
  def apply(address: Address): AddressView = {
    import address._
    AddressView(addressLine, city, postalCode, country)
  }
}