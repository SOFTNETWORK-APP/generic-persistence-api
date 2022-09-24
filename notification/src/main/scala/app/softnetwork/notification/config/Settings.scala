package app.softnetwork.notification.config

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

object Settings extends StrictLogging {

  lazy val config: Config = ConfigFactory.load()

  lazy val NotificationConfig: NotificationConfig = Configs[NotificationConfig].get(config, "notification").toEither match {
    case Left(configError) =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      throw configError.configException
    case Right(r) => r
  }

  import scala.language.implicitConversions
  import scala.collection.JavaConverters._

  lazy val PushConfigs: Map[String, PushConfig] = config.getStringList("notification.push.apps").asScala.toList.map(app =>
    app -> Configs[PushConfig].get(config, s"notification.push.$app").value
  ).toMap
}

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

case class FcmConfig(databaseUrl: String, googleCredentials: Option[String])

case class EventStreams(externalToNotificationTag: String)

case class NotificationConfig(mail: MailConfig,
                              push: PushConfig,
                              sms : SMSConfig,
                              eventStreams: EventStreams,
                              akkaNodeRole: String)

