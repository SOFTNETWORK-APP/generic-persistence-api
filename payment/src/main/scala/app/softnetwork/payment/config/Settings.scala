package app.softnetwork.payment.config

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

/**
  * Created by smanciot on 05/07/2018.
  */
object Settings extends StrictLogging {

  lazy val config: Config = ConfigFactory.load()

  lazy val MangoPayConfig: MangoPay.Config = Configs[MangoPay.Config].get(config, "payment.mangopay").toEither match{
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(mangoPayConfig) => mangoPayConfig
  }

  val BaseUrl: String = config.getString("payment.baseUrl")

  val PaymentPath: String = config.getString("payment.path")

  val PayInRoute: String = config.getString("payment.payIn-route")
  val PayInStatementDescriptor: String = config.getString("payment.payIn-statement-descriptor")
  val PreAuthorizeCardRoute: String = config.getString("payment.pre-authorize-card-route")
  val RecurringPaymentRoute: String = config.getString("payment.recurringPayment-route")
  val SecureModeRoute: String = config.getString("payment.secure-mode-route")
  val HooksRoute: String = config.getString("payment.hooks-route")
  val MandateRoute: String = config.getString("payment.mandate-route")
  val CardRoute: String = config.getString("payment.card-route")
  val BankRoute: String = config.getString("payment.bank-route")
  val DeclarationRoute: String = config.getString("payment.declaration-route")
  val KycRoute: String = config.getString("payment.kyc-route")

  val ExternalToPaymentAccountTag: String =
    config.getString("payment.event-streams.external-to-payment-account-tag")
}
