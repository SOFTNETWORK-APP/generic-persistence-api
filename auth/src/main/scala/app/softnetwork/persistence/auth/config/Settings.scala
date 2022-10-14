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

  val AkkaNodeRole: String = config.getString("auth.akka-node-role")

  val Realm: String = config.getString("auth.realm")

  val Path: String = config.getString("auth.path")

  val BaseUrl: String = config.getString("auth.baseUrl")

  val ResetPasswordUrl: String = config.getString("auth.resetPasswordUrl")

  val RegenerationOfThePasswordResetToken: Boolean = config.getBoolean("auth.regenerationOfThePasswordResetToken")

  val MailFrom: String = config.getString("notification.mail.from")

  val MailName: String = config.getString("notification.mail.name")

  val ActivationEnabled: Boolean = config.getBoolean("auth.activation.enabled")

  val ActivationTokenExpirationTime: Int = config.getInt("auth.activation.token.expirationTime")

  val VerificationCodeSize: Int = config.getInt("auth.verification.code.size")

  val VerificationCodeExpirationTime: Int = config.getInt("auth.verification.code.expirationTime")

  val VerificationTokenExpirationTime: Int = config.getInt("auth.verification.token.expirationTime")

  val PushClientId: String = config.getString("notification.push.clientId")

  val SMSClientId: String = config.getString("notification.sms.clientId")

  val SMSName: String = config.getString("notification.sms.name")

  val MaxLoginFailures: Int = config.getInt("auth.maxLoginFailures")

  def passwordRules(config: Config = config): PasswordRules = Configs[PasswordRules].get(config, "auth.password").toEither match{
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

  val VerificationGsmEnabled: Boolean = config.getBoolean("auth.verification.gsm.enabled")

  val VerificationEmailEnabled: Boolean = config.getBoolean("auth.verification.email.enabled")

  val AnonymousPassword: String = config.getString("auth.anonymous.password")
}

