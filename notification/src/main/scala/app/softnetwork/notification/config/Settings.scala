package app.softnetwork.notification.config

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

object Settings extends StrictLogging {

  lazy val Config: NotificationConfig = Configs[NotificationConfig].get(ConfigFactory.load(), "notification").toEither match {
    case Left(configError) =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(r) => r
  }

  case class NotificationConfig(mail: MailConfig, push: PushConfig, sms : SMSConfig)

  case class MailConfig(host: String,
                        port: Int,
                        sslPort: Int,
                        username: String,
                        password: String,
                        sslEnabled: Boolean,
                        sslCheckServerIdentity: Boolean,
                        startTLSEnabled: Boolean,
                        socketConnectionTimeout: Int = 2000,
                        socketTimeout: Int = 2000)

  case class PushConfig(apns: ApnsConfig, gcm: GcmConfig, fcm: FcmConfig)

  case class ApnsConfig(topic: String, keystore: Keystore, dryRun: Boolean = false)

  case class Keystore(path: String, password: String = "")

  case class GcmConfig(apiKey: String)

  case class SMSConfig(mode: Option[SMSMode.Config] = None)

  case class FcmConfig(databaseUrl: String)
}
