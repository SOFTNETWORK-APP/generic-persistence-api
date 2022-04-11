package app.softnetwork.persistence.auth.config

/**
  * Created by smanciot on 08/04/2018.
  */
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs
import Password._

object Settings extends StrictLogging {

  lazy val config: Config = ConfigFactory.load()

  val Path = config.getString("auth.path")

  val BaseUrl = config.getString("auth.baseUrl")

  val ResetPasswordUrl = config.getString("auth.resetPasswordUrl")

  val RegenerationOfThePasswordResetToken = config.getBoolean("auth.regenerationOfThePasswordResetToken")

  val MailFrom = config.getString("notification.mail.from")

  val MailName = config.getString("notification.mail.name")

  val ActivationEnabled = config.getBoolean("auth.activation.enabled")

  val ActivationTokenExpirationTime = config.getInt("auth.activation.token.expirationTime")

  val VerificationCodeSize = config.getInt("auth.verification.code.size")

  val VerificationCodeExpirationTime = config.getInt("auth.verification.code.expirationTime")

  val VerificationTokenExpirationTime = config.getInt("auth.verification.token.expirationTime")

  val PushClientId = config.getString("notification.push.clientId")

  val SMSClientId = config.getString("notification.sms.clientId")

  val SMSName = config.getString("notification.sms.name")

  val MaxLoginFailures = config.getInt("auth.maxLoginFailures")

  def passwordRules(config: Config = config) = Configs[PasswordRules].get(config, "auth.password").toEither match{
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      PasswordRules()
    case Right(rules) => rules
  }

  lazy val NotificationsConfig: Notifications.Config = Configs[Notifications.Config].get(config, "auth.notifications").toEither match{
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(notificationsConfig) => notificationsConfig
  }

  lazy val AdministratorsConfig: Administrators.Config = Configs[Administrators.Config].get(
    config, "auth.admin"
  ).toEither match {
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(administratorsConfig) => administratorsConfig
  }

  val VerificationGsmEnabled = config.getBoolean("auth.verification.gsm.enabled")

  val VerificationEmailEnabled = config.getBoolean("auth.verification.email.enabled")
}

