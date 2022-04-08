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

  val Path = config.getString("security.account.path")

  val BaseUrl = config.getString("security.baseUrl")

  val ResetPasswordUrl = config.getString("security.resetPasswordUrl")

  val RegenerationOfThePasswordResetToken = config.getBoolean("security.regenerationOfThePasswordResetToken")

  val MailFrom = config.getString("notification.mail.from")

  val MailName = config.getString("notification.mail.name")

  val ActivationEnabled = config.getBoolean("security.activation.enabled")

  val ActivationTokenExpirationTime = config.getInt("security.activation.token.expirationTime")

  val VerificationCodeSize = config.getInt("security.verification.code.size")

  val VerificationCodeExpirationTime = config.getInt("security.verification.code.expirationTime")

  val VerificationTokenExpirationTime = config.getInt("security.verification.token.expirationTime")

  val PushClientId = config.getString("notification.push.clientId")

  val SMSClientId = config.getString("notification.sms.clientId")

  val SMSName = config.getString("notification.sms.name")

  val MaxLoginFailures = config.getInt("security.maxLoginFailures")

  def passwordRules(config: Config = config) = Configs[PasswordRules].get(config, "security.password").toEither match{
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      PasswordRules()
    case Right(rules) => rules
  }

  lazy val NotificationsConfig: Notifications.Config = Configs[Notifications.Config].get(config, "security.notifications").toEither match{
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(notificationsConfig) => notificationsConfig
  }

  lazy val AdministratorsConfig: Administrators.Config = Configs[Administrators.Config].get(
    config, "security.admin"
  ).toEither match {
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(administratorsConfig) => administratorsConfig
  }

  val VerificationGsmEnabled = config.getBoolean("security.verification.gsm.enabled")

  val VerificationEmailEnabled = config.getBoolean("security.verification.email.enabled")
}

