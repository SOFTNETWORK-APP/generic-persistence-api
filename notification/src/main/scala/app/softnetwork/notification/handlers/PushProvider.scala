package app.softnetwork.notification.handlers

import java.io.{FileInputStream, File => JFile}
import java.time.Duration
import java.util.Date
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import com.eatthepath.pushy.apns.{ApnsClient, ApnsClientBuilder, PushNotificationResponse}
import com.eatthepath.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPayloadBuilder, SimpleApnsPushNotification}
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.messaging._
import com.typesafe.scalalogging.StrictLogging
import app.softnetwork.config.{Settings => CommonSettings}
import app.softnetwork.notification.config.{ApnsConfig, FcmConfig, Settings}
import org.softnetwork.notification.model._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 14/04/2018.
  */
trait PushProvider extends NotificationProvider[Push] with Completion with StrictLogging {

  val maxDevices = 1000

  override def send(notification: Push)(implicit system: ActorSystem[_]): NotificationAck = {
    // split notification per platform
    val (android, ios) = notification.devices.partition(_.platform == Platform.ANDROID)

    // send notification to devices per platform
    NotificationAck(
      None,
      apns(notification, ios.map(_.regId).distinct) ++ fcm(notification, android.map(_.regId)).distinct,
      new Date()
    )
  }

  @tailrec
  private def apns(
                    notification: Push,
                    devices: Seq[String],
                    status: Seq[NotificationStatusResult] = Seq.empty
                  )(implicit system: ActorSystem[_]): Seq[NotificationStatusResult] = {
    import APNSPushProvider._

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val nbDevices: Int = devices.length
    if(nbDevices > 0){
      val tos =
        if(nbDevices > maxDevices)
          devices.take(maxDevices)
        else
          devices

      val from = notification.from
      val key = from.alias.getOrElse(from.value)
      val _config = config(key)
      val _client = client(key, _config)

      logger.info(
        s"""APNS -> about to send notification ${notification.subject}
           |\tfrom ${notification.from.value}
           |\tvia topic ${_config.topic}
           |\tto token(s) [${tos.mkString(",")}]
           |\tusing keystore ${_config.keystore.path}""".stripMargin
      )

      val results =
        Future.sequence(for(to <- tos) yield {
          toScala(_client.sendNotification(
            new SimpleApnsPushNotification(to, _config.topic, notification))
          )
        }) complete() match {
          case Success(responses) =>
            for(response <- responses) yield {
              val result: NotificationStatusResult = response
              logger.info(s"send push to APNS -> $result")
              result
            }
          case Failure(f) =>
            logger.error(s"send push to APNS -> ${f.getMessage}", f)
            tos.map(to => NotificationStatusResult(to, NotificationStatus.Undelivered, Some(f.getMessage)))
        }
      if(nbDevices > maxDevices){
        apns(notification, devices.drop(maxDevices), status ++ results)
      }
      else{
        status ++ results
      }
    }
    else {
      logger.warn("send push to APNS -> no IOS device(s)")
      status
    }
  }

  @tailrec
  private def fcm(
                   notification: Push,
                   devices: Seq[String],
                   status: Seq[NotificationStatusResult] = Seq.empty
                 ): Seq[NotificationStatusResult] = {
    import FCMPushProvider._
    val nbDevices: Int = devices.length
    if(nbDevices > 0){
      implicit val tokens: Seq[String] =
        if(nbDevices > maxDevices)
          devices.take(maxDevices)
        else
          devices

      val from = notification.from
      val key = from.alias.getOrElse(from.value)
      val _config = config(key)
      val _app = app(key, _config)

      logger.info(
        s"""FCM -> about to send notification ${notification.subject}
           |\tfrom $key
           |\tvia url ${_config.databaseUrl}
           |\tto token(s) [${tokens.mkString(",")}]
           |\tusing credentials ${_config.googleCredentials.getOrElse(sys.env.get("GOOGLE_APPLICATION_CREDENTIALS"))}"""
          .stripMargin
      )

      val results: Seq[NotificationStatusResult] =
        Try(
          FirebaseMessaging.getInstance(_app).sendMulticast(notification)
        ) match {
          case Success(s) =>
            val results: Seq[NotificationStatusResult] = s
            logger.info(s"send push to FCM -> ${results.mkString("|")}")
            results
          case Failure(f) =>
            logger.error(s"send push to FCM -> ${f.getMessage}", f)
            tokens.map(token => NotificationStatusResult(token, NotificationStatus.Undelivered, Some(f.getMessage)))
        }
      if(nbDevices > maxDevices){
        fcm(notification, devices.drop(maxDevices), status ++ results)
      }
      else{
        status ++ results
      }
    }
    else{
      logger.warn("send push to FCM -> no ANDROID device(s)")
      status
    }
  }

}

object APNSPushProvider {

  private[this] var clients: Map[String, ApnsClient] = Map.empty

  private[this] var configs: Map[String, ApnsConfig] = Map.empty

  private[notification] def client(key: String, apnsConfig: ApnsConfig): ApnsClient = {
    clients.get(key) match {
      case Some(client) => client
      case _ =>
        val client: ApnsClient =
          clientCredentials(apnsConfig)(
            new ApnsClientBuilder()
              .setApnsServer(
                if (apnsConfig.dryRun) {
                  ApnsClientBuilder.DEVELOPMENT_APNS_HOST
                } else {
                  ApnsClientBuilder.PRODUCTION_APNS_HOST
                }
              )
          ).setConnectionTimeout(Duration.ofSeconds(CommonSettings.DefaultTimeout.toSeconds)).build()
        clients = clients + (key -> client)
        client
    }
  }

  private[notification] def config(key: String): ApnsConfig = {
    configs.get(key) match {
      case Some(config) => config
      case _ =>
        val config: ApnsConfig = Settings.PushConfigs.get(key).map(_.apns) match {
          case Some(apnsConfig) => apnsConfig
          case _ => Settings.NotificationConfig.push.apns
        }
        configs = configs + (key -> config)
        config
    }
  }

  implicit def toApnsPayload(notification: Push): String = {
    val apnsPayload = new SimpleApnsPayloadBuilder()
      .setAlertTitle(notification.subject)
      .setAlertBody(notification.message)
      .setSound(notification.sound.getOrElse(ApnsPayloadBuilder.DEFAULT_SOUND_FILENAME))
    if(notification.badge > 0) {
      apnsPayload.setBadgeNumber(notification.badge)
    }
    apnsPayload.build()
  }

  implicit def toNotificationStatusResult(result: PushNotificationResponse[SimpleApnsPushNotification]): NotificationStatusResult = {
    val error = Option(result.getRejectionReason) match {
      case Some(e) => Some(s"${result.getPushNotification.getToken} -> $e")
      case _ => None
    }
    NotificationStatusResult(
      result.getPushNotification.getToken,
      if (result.isAccepted)
        NotificationStatus.Sent
      else
        NotificationStatus.Rejected,
      error
    )
  }

  def clientCredentials(apnsConfig: ApnsConfig): ApnsClientBuilder => ApnsClientBuilder = builder => {
    val file = new JFile(apnsConfig.keystore.path)
    if(file.exists){
      builder.setClientCredentials(file, apnsConfig.keystore.password)
    }
    else{
      builder.setClientCredentials(
        getClass.getClassLoader.getResourceAsStream(apnsConfig.keystore.path),
        apnsConfig.keystore.password
      )
    }
  }

}

object FCMPushProvider{

  private[this] var apps: Map[String, FirebaseApp] = Map.empty

  private[notification] def app(key: String, fcmConfig: FcmConfig): FirebaseApp = {
    apps.get(key) match {
      case Some(app) => app
      case _ =>
        val app = FirebaseApp.initializeApp(
          clientCredentials(fcmConfig)(FirebaseOptions.builder()).setDatabaseUrl(fcmConfig.databaseUrl).build(), key
        )
        apps = apps + (key -> app)
        app
    }
  }

  private[notification] def config(key: String): FcmConfig = {
    Settings.PushConfigs.get(key).map(_.fcm).getOrElse(Settings.NotificationConfig.push.fcm)
  }

  def clientCredentials(fcmConfig: FcmConfig): FirebaseOptions.Builder => FirebaseOptions.Builder = builder => {
    fcmConfig.googleCredentials match {
      case Some(googleCredentials) if googleCredentials.trim.nonEmpty =>
        builder.setCredentials(GoogleCredentials.fromStream(new FileInputStream(new JFile(googleCredentials))))
      case _ => builder.setCredentials(GoogleCredentials.getApplicationDefault())
    }
  }

  implicit def toFcmPayload(notification: Push)(implicit tokens: Seq[String]): MulticastMessage = {
    val androidNotification = AndroidNotification.builder()
      .setTitle(notification.subject)
      .setBody(notification.message)
      .setSound(notification.sound.getOrElse("default"))
    if(notification.badge > 0){
      androidNotification.setNotificationCount(notification.badge)
    }
    val payload = MulticastMessage.builder()
      .setAndroidConfig(
        AndroidConfig.builder().setNotification(androidNotification.build()).build()
      )
      .addAllTokens(tokens.asJava)
      .build()
    payload
  }

  implicit def toNotificationResults(response: BatchResponse)(implicit tokens: Seq[String]): Seq[NotificationStatusResult] = {
    for((r, i) <- response.getResponses.asScala.zipWithIndex) yield
      NotificationStatusResult(
        tokens(i),
        if (r.isSuccessful)
          NotificationStatus.Sent
        else
          NotificationStatus.Rejected,
        Option(r.getException).map(e => e.getMessage)
      )
  }
}

trait MockPushProvider extends PushProvider with MockNotificationProvider[Push]

object PushProvider extends PushProvider

object MockPushProvider extends MockPushProvider
