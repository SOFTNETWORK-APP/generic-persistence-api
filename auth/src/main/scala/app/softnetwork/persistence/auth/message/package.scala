package app.softnetwork.persistence.auth

import akka.http.scaladsl.server.directives.Credentials
import app.softnetwork.persistence.message._
import org.softnetwork.notification.model.NotificationType
import app.softnetwork.persistence.auth.model._
import org.softnetwork.notification.message.{NotificationCommandEvent, WrapNotificationCommandEvent}

/**
  * Created by smanciot on 17/04/2020.
  */
package object message {

  /**
    * Created by smanciot on 19/03/2018.
    */
  /*sealed */trait AccountCommand extends Command

  @SerialVersionUID(0L)
  case class SignUp(
                     login: String,
                     password: String,
                     confirmPassword: Option[String] = None,
                     firstName: Option[String] = None,
                     lastName: Option[String] = None,
                     userName: Option[String] = None
                   ) extends AccountCommand

  case object SignUpAnonymous extends AccountCommand

  @SerialVersionUID(0L)
  case class Unsubscribe(uuid: String) extends AccountCommand

  case object DestroyAccount extends AccountCommand

  sealed trait LookupAccountCommand  extends AccountCommand

  case class BasicAuth(credentials: Credentials.Provided) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class Login(login: String, password: String, refreshable: Boolean = false, anonymous: Option[String] = None)
    extends LookupAccountCommand

  case object Logout extends AccountCommand

  @SerialVersionUID(0L)
  case class SendResetPasswordToken(principal: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class SendVerificationCode(principal: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class CheckResetPasswordToken(token: String) extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class ResetPassword(token: String, newPassword: String, confirmedPassword: Option[String] = None)
    extends LookupAccountCommand

  @SerialVersionUID(0L)
  case class PasswordData(oldPassword: String, newPassword: String, confirmedPassword: Option[String] = None)

  @SerialVersionUID(0L)
  case class UpdatePassword(login: String, oldPassword: String, newPassword: String, confirmedPassword: Option[String] = None)
    extends AccountCommand

  @SerialVersionUID(0L)
  case class Activate(token: String) extends LookupAccountCommand

  sealed trait DeviceCommand extends AccountCommand

  @SerialVersionUID(0L)
  case class RegisterDevice(uuid: String, registration: DeviceRegistration) extends DeviceCommand

  @SerialVersionUID(0L)
  case class UnregisterDevice(uuid: String, regId: String, deviceId: Option[String] = None) extends DeviceCommand

  @SerialVersionUID(0L)
  case class UpdateProfile(uuid: String, profile: Profile) extends AccountCommand

  @SerialVersionUID(0L)
  case class SwitchProfile(uuid: String, name: String) extends AccountCommand

  @SerialVersionUID(0L)
  case class LoadProfile(uuid: String, name: Option[String]) extends AccountCommand

  @SerialVersionUID(0L)
  case class UpdateLogin(oldLogin: String, newLogin: String, password: String) extends AccountCommand

  @SerialVersionUID(0L)
  class InitAdminAccount(login: String, password: String) extends SignUp(login, password)

  case class RecordNotification(uuids: Set[String],
                                channel: NotificationType,
                                subject: String,
                                content: String) extends AccountCommand

  case class WrapInternalAccountEvent(event: InternalAccountEvent) extends AccountCommand

  /**
    * Created by smanciot on 19/03/2018.
    */
  trait AccountCommandResult extends CommandResult

  case object LogoutSucceeded extends AccountCommandResult

  case object VerificationCodeSent extends AccountCommandResult

  case object ResetPasswordTokenSent extends AccountCommandResult

  case object ResetPasswordTokenChecked  extends AccountCommandResult

  @SerialVersionUID(0L)
  case class PasswordReseted(uuid: String) extends AccountCommandResult

  sealed trait DeviceCommandResult extends AccountCommandResult

  case object DeviceRegistered extends DeviceCommandResult

  case object DeviceUnregistered extends DeviceCommandResult

  case object ProfileUpdated extends AccountCommandResult

  @SerialVersionUID(0L)
  case class ProfileSwitched(profile: Option[Profile]) extends AccountCommandResult

  @SerialVersionUID(0L)
  case class ProfileLoaded(profile: Profile) extends AccountCommandResult

  case object AdminAccountInitialized extends AccountCommandResult

  case class LoginSucceededResult(account: Account) extends AccountCommandResult

  case class AccountCreated(account: Account) extends AccountCommandResult

  case class AccountActivated(account: Account) extends AccountCommandResult

  case class AccountDeleted(account: Account) extends AccountCommandResult

  case class AccountDestroyed(uuid: String) extends AccountCommandResult

  case class PasswordUpdated(account: Account) extends AccountCommandResult

  case class AccountNotificationSent(uuid: String, notificationUuid: String) extends AccountCommandResult

  case object LoginUpdated extends AccountCommandResult

  case object NotificationRecorded extends AccountCommandResult

  /**
    * Created by smanciot on 19/03/2018.
    */
  @SerialVersionUID(0L)
  class AccountErrorMessage(override val message: String) extends ErrorMessage(message) with AccountCommandResult

  case object LoginAlreadyExists extends AccountErrorMessage("LoginAlreadyExists")

  case object LoginUnaccepted extends AccountErrorMessage("LoginUnaccepted")

  case object AccountDisabled extends AccountErrorMessage("AccountDisabled")

  case object PasswordsNotMatched extends AccountErrorMessage("PasswordsNotMatched")

  case object LoginAndPasswordNotMatched extends AccountErrorMessage("LoginAndPasswordNotMatched")

  case object UndeliveredActivationToken extends AccountErrorMessage("UndeliveredActivationToken")

  case object NewResetPasswordTokenSent extends AccountErrorMessage("NewResetPasswordTokenSent")

  case object UndeliveredResetPasswordToken extends AccountErrorMessage("UndeliveredResetPasswordToken")

  case object TokenNotFound extends AccountErrorMessage("TokenNotFound")

  case object TokenExpired extends AccountErrorMessage("TokenExpired")

  case object InvalidToken extends AccountErrorMessage("InvalidToken")

  case object AccountNotFound extends AccountErrorMessage("AccountNotFound")

  case object AccountAlreadyExists extends AccountErrorMessage("AccountAlreadyExists")

  @SerialVersionUID(0L)
  case class InvalidPassword(errors: Seq[String] = Seq.empty) extends AccountErrorMessage(errors.mkString(","))

  case object IllegalStateError extends AccountErrorMessage("IllegalStateError")

  case object InvalidPrincipal extends AccountErrorMessage("InvalidPrincipal")

  case object UndeliveredVerificationCode extends AccountErrorMessage("UndeliveredVerificationCode")

  case object CodeNotFound extends AccountErrorMessage("CodeNotFound")

  case object CodeExpired extends AccountErrorMessage("CodeExpired")

  case object DeviceRegistrationNotFound extends AccountErrorMessage("DeviceRegistrationNotFound") with DeviceCommandResult

  case object ProfileNotFound extends AccountErrorMessage("ProfileNotFound")

  case object GsmAlreadyExists extends AccountErrorMessage("GsmAlreadyExists")

  case object EmailAlreadyExists extends AccountErrorMessage("EmailAlreadyExists")

  case object UsernameAlreadyExists extends AccountErrorMessage("UsernameAlreadyExists")

  case class AccountNotificationNotSent(uuid: String, reason: Option[String]) extends AccountErrorMessage("AccountNotificationNotSent")

  case object LoginNotUpdated extends AccountErrorMessage("LoginNotUpdated")

  case object InternalAccountEventNotHandled extends AccountErrorMessage("InternalAccountEventNotHandled")

  trait AccountToNotificationCommandEventDecorator extends WrapNotificationCommandEvent{_: AccountToNotificationCommandEvent =>
    override def event: NotificationCommandEvent =
      wrapped match {
        case _: AccountToNotificationCommandEvent.Wrapped.AddMail => getAddMail
        case _: AccountToNotificationCommandEvent.Wrapped.AddSMS => getAddSMS
        case _: AccountToNotificationCommandEvent.Wrapped.AddPush => getAddPush
        case _: AccountToNotificationCommandEvent.Wrapped.RemoveNotification => getRemoveNotification
        case _ => new NotificationCommandEvent {
          override def uuid: String = ""
        }
      }
  }
}
