package app.softnetwork.persistence.auth.persistence.typed

import java.util.Date
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import mustache.Mustache
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import app.softnetwork.notification.handlers.{MockNotificationDao, NotificationDao}
import org.softnetwork.notification.model._
import org.softnetwork.notification.model.NotificationType
import app.softnetwork.notification.serialization._
import app.softnetwork.persistence.auth.config.Settings._
import app.softnetwork.persistence.auth.model._

import scala.language.{implicitConversions, postfixOps}

trait AccountNotifications[T <: Account] extends Completion {

  def notificationDao: NotificationDao = NotificationDao

  /** number of login failures authorized before disabling user account **/
  val maxLoginFailures: Int = MaxLoginFailures

  protected def activationTokenUuid(entityId: String): String = {
    s"$entityId-activation-token"
  }

  protected def registrationUuid(entityId: String): String = {
    s"$entityId-registration"
  }

  private[this] def sendMail(
                              uuid: String,
                              account: T,
                              subject: String,
                              body: String,
                              maxTries: Int,
                              deferred: Option[Date])(implicit system: ActorSystem[_]): Boolean = {
    account.email match {
      case Some(email) =>
        notificationDao.sendNotification(
          Mail.defaultInstance
            .withUuid(uuid)
            .withFrom(From(MailFrom, Some(MailName)))
            .withTo(Seq(email))
            .withSubject(subject)
            .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
            .withRichMessage(body)
            .withMaxTries(maxTries)
            .withDeferred(deferred.orNull)
        ) complete ()
      case _ => false
    }
  }

  private[this] def sendSMS(
                             uuid: String,
                             account: T,
                             subject: String,
                             body: String,
                             maxTries: Int,
                             deferred: Option[Date])(implicit system: ActorSystem[_]): Boolean = {
    account.gsm match {
      case Some(gsm) =>
        notificationDao.sendNotification(
          SMS.defaultInstance
            .withUuid(uuid)
            .withFrom(From(SMSClientId, Some(SMSName)))
            .withTo(Seq(gsm))
            .withSubject(subject)
            .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
            .withMaxTries(maxTries)
            .withDeferred(deferred.orNull)
        ) complete ()
      case _ => false
    }
  }

  private[this] def sendPush(
                              uuid: String,
                              account: T,
                              subject: String,
                              body: String,
                              maxTries: Int,
                              deferred: Option[Date])(implicit system: ActorSystem[_]): Boolean = {
    account.registrations.isEmpty ||
      (notificationDao.sendNotification(
        Push.defaultInstance
          .withUuid(uuid)
          .withFrom(From.defaultInstance.withValue(PushClientId))
          .withSubject(subject)
          .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
          .withDevices(account.registrations.map(registration => BasicDevice(registration.regId, registration.platform)))
          .withMaxTries(maxTries)
          .withDeferred(deferred.orNull)
      )complete ())
  }

  private[this] def sendNotificationByChannel(
                                               uuid: String,
                                               account: T,
                                               subject: String,
                                               body: String,
                                               channel: NotificationType,
                                               maxTries: Int,
                                               deferred: Option[Date])(
                                               implicit system: ActorSystem[_]): Boolean = {
    channel match {
      case NotificationType.MAIL_TYPE => sendMail(uuid, account, subject, body, maxTries, deferred)
      case NotificationType.SMS_TYPE  => sendSMS(uuid, account, subject, body, maxTries, deferred)
      case NotificationType.PUSH_TYPE => sendPush(uuid, account, subject, body, maxTries, deferred)
      case _ => false
    }
  }

  def sendNotification(
                        uuid: String,
                        account: T,
                        subject: String,
                        body: String,
                        channels: Seq[NotificationType],
                        maxTries: Int = 1,
                        deferred: Option[Date] = None)(
                        implicit log: Logger, system: ActorSystem[_]): Boolean = {
    log.info(s"about to send notification to ${account.primaryPrincipal.value}\r\n$body")
    channels.par.map(channel => sendNotificationByChannel(uuid, account, subject, body, channel, maxTries, deferred))
      .exists(p => p)
  }

  def sendActivation(
                      uuid: String,
                      account: T,
                      activationToken: VerificationToken,
                      maxTries: Int = 3,
                      deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.activation

    val body = Mustache("notification/activation.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "activationUrl" -> s"$BaseUrl/$Path/activate/${activationToken.token}"
      )
    )

    sendNotification(
      activationTokenUuid(uuid),
      account,
      subject,
      body,
      NotificationsConfig.channels.activation,
      maxTries,
      deferred
    )
  }

  def sendRegistration(
                        uuid: String,
                        account: T,
                        maxTries: Int = 2,
                        deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.registration

    val body = Mustache("notification/registration.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        })
      )
    )

    sendNotification(
      registrationUuid(uuid),
      account,
      subject,
      body,
      NotificationsConfig.channels.registration,
      maxTries,
      deferred
    )
  }

  def sendVerificationCode(
                            uuid: String,
                            account: T,
                            verificationCode: VerificationCode,
                            maxTries: Int = 1,
                            deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/verification_code.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "code" -> verificationCode.code
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.resetPassword,
      maxTries,
      deferred
    )
  }

  def sendAccountDisabled(
                           uuid: String,
                           account: T,
                           maxTries: Int = 1,
                           deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.accountDisabled

    val body = Mustache("notification/account_disabled.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "resetPasswordUrl" -> ResetPasswordUrl,
        "loginFailures" -> (maxLoginFailures + 1)
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.accountDisabled,
      maxTries,
      deferred
    )
  }

  def sendResetPassword(
                         uuid: String,
                         account: T,
                         verificationToken: VerificationToken,
                         maxTries: Int = 1,
                         deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/reset_password.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "token" -> verificationToken.token,
        "principal" -> account.principal.value,
        "resetPasswordUrl" -> ResetPasswordUrl
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.resetPassword,
      maxTries,
      deferred
    )
  }

  def sendPasswordUpdated(
                           uuid: String,
                           account: T,
                           maxTries: Int = 1,
                           deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.passwordUpdated

    val body = Mustache("notification/password_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        })
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.passwordUpdated,
      maxTries,
      deferred
    )
  }

  def sendPrincipalUpdated(
                            uuid: String,
                            account: T,
                            maxTries: Int = 1,
                            deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Boolean = {
    val subject = NotificationsConfig.principalUpdated

    val body = Mustache("notification/principal_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        })
      )
    )

    sendNotification(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.principalUpdated,
      maxTries,
      deferred
    )
  }

  def removeActivation(uuid: String)(implicit system: ActorSystem[_]): Boolean = removeNotification(
    activationTokenUuid(uuid), Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE)
  )

  def removeRegistration(uuid: String)(implicit system: ActorSystem[_]): Boolean = removeNotification(
    registrationUuid(uuid), Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE)
  )

  private[this] def removeNotification(uuid: String, channels: Seq[NotificationType] = Seq.empty)(
    implicit system: ActorSystem[_]): Boolean = {
    channels.par.forall{_ => notificationDao.removeNotification(uuid) complete ()}
  }

}

trait MockAccountNotifications[T <: Account] extends AccountNotifications[T] {
  override def notificationDao: NotificationDao = MockNotificationDao
}
