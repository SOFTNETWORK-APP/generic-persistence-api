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

}
