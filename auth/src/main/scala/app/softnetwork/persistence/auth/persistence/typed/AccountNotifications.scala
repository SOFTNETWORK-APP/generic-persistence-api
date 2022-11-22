package app.softnetwork.persistence.auth.persistence.typed

import java.util.Date
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import mustache.Mustache
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import org.softnetwork.notification.model._
import org.softnetwork.notification.model.NotificationType
import app.softnetwork.notification.serialization._
import app.softnetwork.persistence.auth.config.Settings._
import app.softnetwork.persistence.auth.message.AccountToNotificationCommandEvent
import app.softnetwork.persistence.auth.model._
import org.softnetwork.notification.message.{AddMailCommandEvent, AddPushCommandEvent, AddSMSCommandEvent, RemoveNotificationCommandEvent}

import scala.language.{implicitConversions, postfixOps}

trait AccountNotifications[T <: Account] extends Completion {

  /** number of login failures authorized before disabling user account **/
  val maxLoginFailures: Int = MaxLoginFailures

  protected def activationTokenUuid(entityId: String): String = {
    s"$entityId-activation-token"
  }

  protected def registrationUuid(entityId: String): String = {
    s"$entityId-registration"
  }

  private[this] def addMail(
                              uuid: String,
                              account: T,
                              subject: String,
                              body: String,
                              maxTries: Int,
                              deferred: Option[Date])(implicit system: ActorSystem[_]): Option[AccountToNotificationCommandEvent] = {
    account.email match {
      case Some(email) =>
          Some(
            AccountToNotificationCommandEvent.defaultInstance.withAddMail(
              AddMailCommandEvent(
                Mail.defaultInstance
                  .withUuid(uuid)
                  .withFrom(From(MailFrom, Some(MailName)))
                  .withTo(Seq(email))
                  .withSubject(subject)
                  .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
                  .withRichMessage(body)
                  .withMaxTries(maxTries)
                  .withDeferred(deferred.orNull)
              )
            )
          )
      case _ => None
    }
  }

  private[this] def addSMS(
                             uuid: String,
                             account: T,
                             subject: String,
                             body: String,
                             maxTries: Int,
                             deferred: Option[Date])(implicit system: ActorSystem[_]): Option[AccountToNotificationCommandEvent] = {
    account.gsm match {
      case Some(gsm) =>
        Some(
          AccountToNotificationCommandEvent.defaultInstance.withAddSMS(
            AddSMSCommandEvent(
              SMS.defaultInstance
                .withUuid(uuid)
                .withFrom(From(SMSClientId, Some(SMSName)))
                .withTo(Seq(gsm))
                .withSubject(subject)
                .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
                .withMaxTries(maxTries)
                .withDeferred(deferred.orNull)
            )
          )
        )
      case _ => None
    }
  }

  private[this] def addPush(
                              uuid: String,
                              account: T,
                              subject: String,
                              body: String,
                              maxTries: Int,
                              deferred: Option[Date])(implicit system: ActorSystem[_]): Option[AccountToNotificationCommandEvent] = {
    if(account.registrations.isEmpty) {
      None
    }
    else {
      Some(
        AccountToNotificationCommandEvent.defaultInstance.withAddPush(
          AddPushCommandEvent(
            Push.defaultInstance
              .withUuid(uuid)
              .withFrom(From.defaultInstance.withValue(PushClientId))
              .withSubject(subject)
              .withMessage(StringEscapeUtils.unescapeHtml4(body).replaceAll("<br/>", "\\\n"))
              .withDevices(account.registrations.map(registration => BasicDevice(registration.regId, registration.platform)))
              .withMaxTries(maxTries)
              .withDeferred(deferred.orNull)
          )
        )
      )
    }
  }

  private[this] def addNotificationByChannel(
                                               uuid: String,
                                               account: T,
                                               subject: String,
                                               body: String,
                                               channel: NotificationType,
                                               maxTries: Int,
                                               deferred: Option[Date])(
                                               implicit system: ActorSystem[_]): Option[AccountToNotificationCommandEvent] = {
    channel match {
      case NotificationType.MAIL_TYPE => addMail(s"mail#$uuid", account, subject, body, maxTries, deferred)
      case NotificationType.SMS_TYPE  => addSMS(s"sms#$uuid", account, subject, body, maxTries, deferred)
      case NotificationType.PUSH_TYPE => addPush(s"push#$uuid", account, subject, body, maxTries, deferred)
      case _ => None
    }
  }

  def addNotifications(
                        uuid: String,
                        account: T,
                        subject: String,
                        body: String,
                        channels: Seq[NotificationType],
                        maxTries: Int = 1,
                        deferred: Option[Date] = None)(
                        implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    log.info(s"about to send notification to ${account.primaryPrincipal.value}\r\n$body")
    channels.flatMap(channel => addNotificationByChannel(uuid, account, subject, body, channel, maxTries, deferred))
  }

  def sendActivation(
                      uuid: String,
                      account: T,
                      activationToken: VerificationToken,
                      maxTries: Int = 3,
                      deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.activation

    val body = Mustache("notification/activation.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "activationUrl" -> s"$BaseUrl/$Path/activate/${activationToken.token}",
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
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
                        deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.registration

    val body = Mustache("notification/registration.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
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
                            deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/verification_code.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "code" -> verificationCode.code,
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
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
                           deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.accountDisabled

    val body = Mustache("notification/account_disabled.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "resetPasswordUrl" -> ResetPasswordUrl,
        "loginFailures" -> (maxLoginFailures + 1),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
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
                         deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.resetPassword

    val body = Mustache("notification/reset_password.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "token" -> verificationToken.token,
        "principal" -> account.principal.value,
        "resetPasswordUrl" -> ResetPasswordUrl,
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
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
                           deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.passwordUpdated

    val body = Mustache("notification/password_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
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
                            deferred: Option[Date] = None)(implicit log: Logger, system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    val subject = NotificationsConfig.principalUpdated

    val body = Mustache("notification/principal_updated.mustache").render(
      Map(
        "firstName" -> (account.details match {
          case Some(s) => StringEscapeUtils.escapeHtml4(s.firstName)
          case _       => "customer"
        }),
        "signature" -> NotificationsConfig.signature
      )
    )

    addNotifications(
      uuid,
      account,
      subject,
      body,
      NotificationsConfig.channels.principalUpdated,
      maxTries,
      deferred
    )
  }

  def removeActivation(uuid: String)(implicit system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = removeNotifications(
    activationTokenUuid(uuid), Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE)
  )

  def removeRegistration(uuid: String)(implicit system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = removeNotifications(
    registrationUuid(uuid), Seq(NotificationType.MAIL_TYPE, NotificationType.PUSH_TYPE, NotificationType.SMS_TYPE)
  )

  private[this] def removeNotifications(uuid: String, channels: Seq[NotificationType] = Seq.empty)(
    implicit system: ActorSystem[_]): Seq[AccountToNotificationCommandEvent] = {
    channels.flatMap(channel => removeNotificationByChannel(channel, uuid))
  }

  private[this] def removeNotificationByChannel(channel: NotificationType, uuid: String): Option[AccountToNotificationCommandEvent] = {
    channel match {
      case NotificationType.MAIL_TYPE =>
        Some(
          AccountToNotificationCommandEvent.defaultInstance.withRemoveNotification(
            RemoveNotificationCommandEvent(s"mail#$uuid")
          )
        )
      case NotificationType.SMS_TYPE  =>
        Some(
          AccountToNotificationCommandEvent.defaultInstance.withRemoveNotification(
            RemoveNotificationCommandEvent(s"sms#$uuid")
        )
    )
      case NotificationType.PUSH_TYPE =>
        Some(
          AccountToNotificationCommandEvent.defaultInstance.withRemoveNotification(
            RemoveNotificationCommandEvent(s"push#$uuid")
          )
        )
      case _ => None
    }
  }
}
