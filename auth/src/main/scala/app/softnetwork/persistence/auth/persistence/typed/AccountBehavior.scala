package app.softnetwork.persistence.auth.persistence.typed

import _root_.akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.security.Sha512Encryption
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import app.softnetwork.persistence.typed._
import app.softnetwork.persistence.auth.config.Settings._
import app.softnetwork.persistence.auth.handlers._
import app.softnetwork.persistence.auth.message._
import Sha512Encryption._
import app.softnetwork.notification.config.{Settings => NotificationSettings}
import app.softnetwork.persistence.auth.model._
import app.softnetwork.persistence._
import app.softnetwork.persistence.auth.config.{Password, Settings}
import app.softnetwork.validation.{EmailValidator, GsmValidator}
import org.softnetwork.notification.message.NotificationCommandEvent

import scala.concurrent.ExecutionContextExecutor
import scala.language.{implicitConversions, postfixOps}
import scala.reflect.ClassTag

trait AccountBehavior[T <: Account with AccountDecorator, P <: Profile]
  extends EntityBehavior[AccountCommand, T, AccountEvent, AccountCommandResult]
    with AccountNotifications[T] { self: Generator =>

  def accountKeyDao: AccountKeyDao = AccountKeyDao

  protected val generator: Generator = this

  protected val rules: Password.PasswordRules = passwordRules()

  protected def createAccount(entityId: String, cmd: SignUp): Option[T]

  protected def createProfileUpdatedEvent(uuid: String, profile: P, loginUpdated: Option[Boolean] = None): ProfileUpdatedEvent[P]

  protected def createAccountCreatedEvent(account: T): AccountCreatedEvent[T]

  /**
    *
    * @return node role required to start this actor
    */
  override def role: String = Settings.AkkaNodeRole

  override protected def tagEvent(entityId: String, event: AccountEvent): Set[String] = {
    event match {
      case _: NotificationCommandEvent =>
        Set(
          NotificationSettings.NotificationConfig.eventStreams.externalToNotificationTag
        )
      case _: AccountCreatedEvent[_] => Set(persistenceId, s"$persistenceId-created")
      case _: AccountActivatedEvent => Set(persistenceId, s"$persistenceId-activated")
      case _: AccountDisabledEvent => Set(persistenceId, s"$persistenceId-disabled")
      case _: AccountDeletedEvent => Set(persistenceId, s"$persistenceId-deleted")
      case _: AccountDestroyedEvent => Set(persistenceId, s"$persistenceId-destroyed")
      case _: ProfileUpdatedEvent[_] => Set(persistenceId, s"$persistenceId-profile-updated")
      case _: LoginUpdatedEvent => Set(persistenceId, s"$persistenceId-login-updated")
      case _: InternalAccountEvent => Set(persistenceId, s"$persistenceId-to-internal")
      case _ => Set(persistenceId)
    }
  }

  override def init(system: ActorSystem[_], maybeRole: Option[String] = None)(implicit tTag: ClassTag[AccountCommand]): Unit = {
    AccountKeyBehavior.init(system, maybeRole)
    super.init(system, maybeRole)
  }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(
                              entityId: String,
                              state: Option[T],
                              command: AccountCommand,
                              replyTo: Option[ActorRef[AccountCommandResult]],
                              timers: TimerScheduler[AccountCommand])(implicit context: ActorContext[AccountCommand]
  ): Effect[AccountEvent, Option[T]] = {
    implicit val log: Logger = context.log
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val ec: ExecutionContextExecutor = system.executionContext
    command match {

      case cmd: InitAdminAccount =>
        import cmd._
        rules.validate(password) match {
          case Left(errorCodes) => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
          case Right(success) if success =>
            state match {
              case Some(account) =>
                Effect.persist[AccountEvent, Option[T]](
                  PasswordUpdatedEvent(
                    entityId,
                    encrypt(password),
                    account.verificationCode,
                    account.verificationToken
                  )
                ).thenRun(_ => AdminAccountInitialized ~> replyTo)
              case _ =>
                createAccount(entityId, cmd) match {
                  case Some(account) =>
                    import account._
                    if(!secondaryPrincipals.exists(principal => lookupAccount(principal.value).isDefined)){
                      Effect.persist[AccountEvent, Option[T]](
                        createAccountCreatedEvent(account)
                      ).thenRun(_ => AdminAccountInitialized ~> replyTo)
                    }
                    else {
                      Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                    }
                  case _ => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
              }
            }
        }

      /** handle signUp **/
      case cmd: SignUp =>
        state match {
          case Some(account) if !account.anonymous.getOrElse(false) =>
            Effect.none.thenRun(_ => AccountAlreadyExists ~> replyTo)
          case _ =>
            import cmd._
            if(confirmPassword.isDefined && !password.equals(confirmPassword.get)){
              Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)//.thenStop()
            }
            else{
              rules.validate(password) match {
                case Left(errorCodes)          => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
                case Right(success) if success =>
                  createAccount(entityId, cmd) match {
                    case Some(account) =>
                      import account._
                      if(!secondaryPrincipals.exists(principal => lookupAccount(principal.value).isDefined)){
                        val activationRequired = status.isInactive
                        val updatedAccount =
                          if(activationRequired) { // an activation is required
                            log.info(s"activation required for ${account.primaryPrincipal.value}")
                            val activationToken = generator.generateToken(
                              account.primaryPrincipal.value,
                              ActivationTokenExpirationTime
                            )
                            accountKeyDao.addAccountKey(activationToken.token, entityId)
                            account
                              .copyWithVerificationToken(Some(activationToken))
                              .copyWithAnonymous(false)
                              .asInstanceOf[T]
                          }
                          else{
                            account
                              .copyWithAnonymous(false)
                              .asInstanceOf[T]
                          }
                        val notifications: Seq[AccountToNotificationCommandEvent] = {
                          updatedAccount.verificationToken match {
                            case Some(verificationToken) =>
                              sendActivation(entityId, account, verificationToken)
                            case _ =>
                              if(updatedAccount.status.isActive){
                                sendRegistration(entityId, updatedAccount)
                              }
                              else{
                                Seq.empty
                              }
                          }
                        }
                        Effect.persist[AccountEvent, Option[T]](
                          notifications.toList :+ createAccountCreatedEvent(updatedAccount)
                        ).thenRun(_ => AccountCreated(updatedAccount) ~> replyTo)
                      }
                      else {
                        Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                      }
                    case _             => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
                  }
              }
            }
        }

      case SignUpAnonymous =>
        state match {
          case Some(_) =>
            Effect.none.thenRun(_ => AccountAlreadyExists ~> replyTo)
          case _ =>
            createAccount(entityId, SignUp(entityId, AnonymousPassword)) match {
              case Some(account) =>
                import account._
                if(!secondaryPrincipals.exists(principal => lookupAccount(principal.value).isDefined)){
                  val updatedAccount = account.copyWithAnonymous(true).asInstanceOf[T]
                  Effect.persist[AccountEvent, Option[T]](
                    createAccountCreatedEvent(updatedAccount)
                  ).thenRun(_ => AccountCreated(updatedAccount) ~> replyTo)
                }
                else {
                  Effect.none.thenRun(_ => LoginAlreadyExists ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => LoginUnaccepted ~> replyTo)
            }
        }

      /** handle account activation **/
      case cmd: Activate =>
        import cmd._
        state match {
          case Some(account) if account.status.isInactive =>
            import account._
            verificationToken match {
              case Some(v) =>
                if(v.expired){
                  accountKeyDao.removeAccountKey(v.token)
                  val activationToken = generator.generateToken(
                    account.primaryPrincipal.value, ActivationTokenExpirationTime
                  )
                  accountKeyDao.addAccountKey(activationToken.token, entityId)
                  val notifications = sendActivation(entityId, account, activationToken)
                  Effect.persist[AccountEvent, Option[T]](
                    notifications.toList :+
                      VerificationTokenAdded(
                        entityId,
                        activationToken
                      )
                  ).thenRun(_ => TokenExpired ~> replyTo)
                }
                else if(v.token != token){
                  Effect.none.thenRun(_ => InvalidToken ~> replyTo)
                }
                else{
                  Effect.persist[AccountEvent, Option[T]](AccountActivatedEvent(entityId).withLastUpdated(now()))
                    .thenRun(state => AccountActivated(state.getOrElse(account)) ~> replyTo)
                }
              case _       => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
            }
          case None    => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          case _       => Effect.none.thenRun(_ => IllegalStateError ~> replyTo)
        }

      case cmd: BasicAuth =>
        import cmd._
        authenticate(credentials.identifier, encrypted => credentials.verify(encrypted, Sha512Encryption.hash(encrypted)), entityId, state, replyTo)

      /** handle login **/
      case cmd: Login =>
        import cmd._
        authenticate(login, encrypted => checkEncryption(encrypted, password), entityId, state, replyTo)

      /** handle send verification code **/
      case cmd: SendVerificationCode =>
        import cmd._
        if(EmailValidator.check(principal) || GsmValidator.check(principal)){
          state match {
            case Some(account) if account.principals.exists(_.value == principal) =>
              account.verificationCode.foreach(v => accountKeyDao.removeAccountKey(v.code))
              val verificationCode = generator.generatePinCode(VerificationCodeSize, VerificationCodeExpirationTime)
              accountKeyDao.addAccountKey(verificationCode.code, entityId)
              val notifications = sendVerificationCode(generateUUID(), account, verificationCode)
              Effect.persist[AccountEvent, Option[T]](
                notifications.toList :+
                  VerificationCodeAdded(
                    entityId,
                    verificationCode
                  ).withLastUpdated(now())
              ).thenRun(_ => VerificationCodeSent ~> replyTo)
            case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          }
        }
        else{
          Effect.none.thenRun(_ => InvalidPrincipal ~> replyTo)
        }

      case cmd: SendResetPasswordToken =>
        import cmd._
        if(EmailValidator.check(principal) || GsmValidator.check(principal)){
          state match {
            case Some(account) if account.principals.exists(_.value == principal) =>
              account.verificationToken.foreach(v => accountKeyDao.removeAccountKey(v.token))
              val verificationToken = generator.generateToken(account.primaryPrincipal.value, VerificationTokenExpirationTime)
              accountKeyDao.addAccountKey(verificationToken.token, entityId)
              val notifications = sendResetPassword(generateUUID(), account, verificationToken)
              Effect.persist[AccountEvent, Option[T]](
                notifications.toList :+
                  VerificationTokenAdded(
                    entityId,
                    verificationToken
                  ).withLastUpdated(now())
              ).thenRun(_ => ResetPasswordTokenSent ~> replyTo)
            case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
          }
        }
        else{
          Effect.none.thenRun(_ => InvalidPrincipal ~> replyTo)
        }

      case cmd: CheckResetPasswordToken =>
        import cmd._
        state match {
          case Some(account) =>
            import account._
            verificationToken match {
              case Some(v) =>
                if(v.expired){
                  if(RegenerationOfThePasswordResetToken){
                    accountKeyDao.removeAccountKey(v.token)
                    val verificationToken = generator.generateToken(
                      account.primaryPrincipal.value, ActivationTokenExpirationTime
                    )
                    accountKeyDao.addAccountKey(verificationToken.token, entityId)
                    val notifications = sendResetPassword(generateUUID(), account, verificationToken)
                    Effect.persist[AccountEvent, Option[T]](
                      notifications.toList :+
                        VerificationTokenAdded(
                          entityId,
                          verificationToken
                        ).withLastUpdated(now())
                    ).thenRun(_ => NewResetPasswordTokenSent ~> replyTo)
                  }
                  else{
                    Effect.none.thenRun(_ => TokenExpired ~> replyTo)
                  }
                }
                else{
                  if(v.token != token){
                    log.warn(s"tokens do not match !!!!!")
                  }
                  Effect.none.thenRun(_ => ResetPasswordTokenChecked ~> replyTo)
                }
              case _       => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: ResetPassword =>
        import cmd._
        val _confirmedPassword = confirmedPassword.getOrElse(newPassword)
        if(!newPassword.equals(_confirmedPassword)){
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)
        }
        else {
          rules.validate(newPassword) match {
            case Left(errorCodes) => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              state match {
                case Some(account) =>
                  import account._
                  if (NotificationsConfig.resetPasswordCode) {
                    verificationCode match {
                      case Some(verification) =>
                        if (!verification.expired) {
                          Effect.persist[AccountEvent, Option[T]](
                            PasswordUpdatedEvent(
                              entityId,
                              encrypt(newPassword),
                              None,
                              account.verificationToken
                            ).withLastUpdated(now())
                          ).thenRun(_ => {
                            accountKeyDao.removeAccountKey(verification.code)
                            PasswordReseted(entityId)
                          } ~> replyTo)
                        }
                        else {
                          Effect.none.thenRun(_ => CodeExpired ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => CodeNotFound ~> replyTo)
                    }
                  }
                  else {
                    verificationToken match {
                      case Some(verification) =>
                        if (!verification.expired) {
                          Effect.persist[AccountEvent, Option[T]](
                            PasswordUpdatedEvent(
                              entityId,
                              encrypt(newPassword),
                              account.verificationCode,
                              None
                            ).withLastUpdated(now())
                          ).thenRun(_ => {
                              accountKeyDao.removeAccountKey(verification.token)
                              PasswordReseted(entityId)
                            } ~> replyTo)
                        }
                        else {
                          Effect.none.thenRun(_ => TokenExpired ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => TokenNotFound ~> replyTo)
                    }
                  }
                case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
              }
          }
        }

      /** handle update password **/
      case cmd: UpdatePassword =>
        import cmd._
        val _confirmedPassword = confirmedPassword.getOrElse(newPassword)
        if(!newPassword.equals(_confirmedPassword)){
          Effect.none.thenRun(_ => PasswordsNotMatched ~> replyTo)
        }
        else{
          rules.validate(newPassword) match {
            case Left(errorCodes)          => Effect.none.thenRun(_ => InvalidPassword(errorCodes) ~> replyTo)
            case Right(success) if success =>
              state match {
                case Some(account) =>
                  import account._
                  if(checkEncryption(credentials, oldPassword)){
                    Effect.persist[AccountEvent, Option[T]](
                      PasswordUpdatedEvent(
                        entityId,
                        encrypt(newPassword),
                        account.verificationCode,
                        account.verificationToken
                      ).withLastUpdated(now())
                    ).thenRun(state => {
                        sendPasswordUpdated(generateUUID(), state.get)
                        PasswordUpdated(state.get)
                      } ~> replyTo)
                  }
                  else {
                    Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
                  }
                case _       => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
              }
          }
        }

      /**
        * handle device registration
        */
      case cmd: RegisterDevice =>
        import cmd._
        state match {
          case Some(_) if entityId == uuid =>
            Effect.persist[AccountEvent, Option[T]](
              DeviceRegisteredEvent(
                entityId,
                registration
              ).withLastUpdated(now())
            ).thenRun(_ => DeviceRegistered ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      /**
        * handle device unregistration
        */
      case cmd: UnregisterDevice =>
        import cmd._
        state match {
          case Some(account) if entityId == uuid =>
            account.registrations.find(_.regId == regId) match {
              case Some(r) =>
                Effect.persist[AccountEvent, Option[T]](
                  DeviceUnregisteredEvent(
                    entityId,
                    r
                  ).withLastUpdated(now())
                ).thenRun(_ => DeviceUnregistered ~> replyTo)
              case _       => Effect.none.thenRun(_ => DeviceRegistrationNotFound ~> replyTo)
            }
          case _       => Effect.none.thenRun(_ => DeviceRegistrationNotFound ~> replyTo)
        }

      /** handle unsubscribe **/
      case _: Unsubscribe        =>
        state match {
          case Some(_) =>
            Effect.persist[AccountEvent, Option[T]](
              AccountDeletedEvent(entityId).withLastUpdated(now())
            ).thenRun(state => AccountDeleted(state.get) ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case _: DestroyAccount.type =>
        state match {
          case Some(_) =>
            Effect.persist[AccountEvent, Option[T]](
              AccountDestroyedEvent(entityId).withLastUpdated(now())
            ).thenRun(_ => AccountDestroyed(entityId) ~> replyTo)
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case _: Logout.type    => Effect.persist(LogoutEvent(entityId, now())).thenRun(_ => LogoutSucceeded ~> replyTo)

      case cmd: UpdateLogin =>
        import cmd._
        state match {
          case Some(account) =>
            val principal = Principal(newLogin.trim)
            if(!account.principals.exists(_.value == oldLogin)){ //check login against account principals
              Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
            }
            else if(!checkEncryption(account.credentials, password)){
              Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
            }
            else if(lookupAccount(newLogin.trim).getOrElse(entityId) != entityId){
              principal.`type` match {
                case PrincipalType.Gsm =>
                  Effect.none.thenRun(_ => GsmAlreadyExists ~> replyTo)
                case PrincipalType.Email =>
                  Effect.none.thenRun(_ => EmailAlreadyExists ~> replyTo)
                case PrincipalType.Username =>
                  Effect.none.thenRun(_ => UsernameAlreadyExists ~> replyTo)
                case _ =>
                  Effect.none.thenRun(_ => LoginNotUpdated ~> replyTo)
              }
            }
            else{
              Effect.persist(
                List(
                  LoginUpdatedEvent(
                    uuid = entityId,
                    principal = principal
                  ).withLastUpdated(now())
                ) ++
                account.profiles.map(kv =>
                  createProfileUpdatedEvent(entityId, kv._2.copyWithPrincipal(principal).asInstanceOf[P], Some(true))
                )
              ).thenRun(_ => {
                sendPrincipalUpdated(generateUUID(), account)
                LoginUpdated ~> replyTo
              })
            }
          case _ => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: UpdateProfile  =>
        import cmd._
        state match {
          case Some(account) =>
            val phoneNumber = profile.phoneNumber.getOrElse("").trim
            val email = profile.email.getOrElse("").trim
            if(VerificationGsmEnabled &&
              phoneNumber.length > 0 &&
              lookupAccount(phoneNumber).getOrElse(entityId) != entityId){
              Effect.none.thenRun(_ => GsmAlreadyExists ~> replyTo)
            }
            else if(VerificationEmailEnabled &&
              email.length > 0 &&
              lookupAccount(email).getOrElse(entityId) != entityId){
              Effect.none.thenRun(_ => EmailAlreadyExists ~> replyTo)
            }
            else{
              Effect.persist[AccountEvent, Option[T]](
                createProfileUpdatedEvent(entityId, account.completeProfile(profile).asInstanceOf[P], Some(false))
              ).thenRun(_ => {
                ProfileUpdated
              } ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: SwitchProfile  =>
        import cmd._
        state match {
          case Some(account) =>
            Effect.persist[AccountEvent, Option[T]](
              ProfileSwitchedEvent(
                entityId,
                name
              ).withLastUpdated(now())
            ).thenRun(_ => {
              ProfileSwitched(account.profile(Some(name)))
            } ~> replyTo)
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: LoadProfile =>
        import cmd._
        state match {
          case Some(account) =>
            account.profile(name) match {
              case Some(profile) => Effect.none.thenRun(_ => ProfileLoaded(profile) ~> replyTo)
              case _             => Effect.none.thenRun(_ => ProfileNotFound ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      case cmd: RecordNotification =>
        import cmd._
        Effect.persist(
          uuids.map(
            AccountNotificationRecordedEvent(
              _,
              channel,
              subject,
              content
            )
          ).toList
        ).thenRun(_ => NotificationRecorded ~> replyTo)

      case cmd: WrapInternalAccountEvent =>
        import cmd._
        state match {
          case Some(account) =>
            event match {
              case evt: AccountNotificationRecordedEvent =>
                import evt._
                val notificationUuid = generateUUID()
                val notifications = addNotifications(
                  notificationUuid,
                  account,
                  subject,
                  if(channel.isMailType)
                    StringEscapeUtils.escapeHtml4(content).replaceAll("\\\n", "<br/>")
                  else
                    StringEscapeUtils.unescapeHtml4(content).replaceAll("<br/>", "\\\n"),
                  Seq(channel)
                )
                Effect.persist(
                  notifications.toList
                ).thenRun(_ => AccountNotificationSent(entityId, notificationUuid) ~> replyTo)
              case _ => Effect.none.thenRun(_ => InternalAccountEventNotHandled ~> replyTo)
            }
          case _             => Effect.none.thenRun(_ => AccountNotFound ~> replyTo)
        }

      /** no handlers **/
      case _ => super.handleCommand(entityId, state, command, replyTo, timers)

    }
  }

  private def authenticate(login: String, verify: String => Boolean, entityId: String, state: Option[T], replyTo: Option[ActorRef[AccountCommandResult]])(implicit log: Logger, system: ActorSystem[_]): Effect[AccountEvent, Option[T]] = {
    state match {
      case Some(account) if account.status.isActive || account.status.isDeleted =>
        val checkLogin = account.principals.exists(_.value == login) //check login against account principal
        if (checkLogin && verify(account.credentials)) {
          Effect.persist[AccountEvent, Option[T]](
            LoginSucceeded(
              entityId,
              now()
            )
          ).thenRun(state => LoginSucceededResult(state.get) ~> replyTo)
        }
        else if (!checkLogin) {
          Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo)
        }
        else { // wrong password
          val nbLoginFailures = account.nbLoginFailures + 1
          val disabled = nbLoginFailures > maxLoginFailures // disable account
          Effect.persist[AccountEvent, Option[T]](
            if (disabled)
              AccountDisabledEvent(
                entityId,
                nbLoginFailures
              ).withLastUpdated(now())
            else
              LoginFailed(
                entityId,
                nbLoginFailures
              ).withLastUpdated(now())
          ).thenRun(_ => {
            if (disabled) {
              if (!account.status.isDisabled) {
                log.info(s"reset password required for ${account.primaryPrincipal.value}")
                sendAccountDisabled(generateUUID(), account)
              }
              AccountDisabled
            }
            else {
              log.info(s"$nbLoginFailures login failure(s) for ${account.primaryPrincipal.value}")
              LoginAndPasswordNotMatched
            }
          } ~> replyTo)
        }
      case Some(account) if account.status.isDisabled =>
        log.info(s"reset password required for ${account.primaryPrincipal.value}")
        sendAccountDisabled(generateUUID(), account)
        Effect.none.thenRun(_ => AccountDisabled ~> replyTo)
      case None => Effect.none.thenRun(_ => LoginAndPasswordNotMatched ~> replyTo) //WrongLogin
      case _ => Effect.none.thenRun(_ => IllegalStateError ~> replyTo)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[T], event: AccountEvent)(
    implicit context: ActorContext[_]): Option[T] = {
    implicit val system: ActorSystem[Nothing] = context.system
    event match {
      case evt: AccountCreatedEvent[_] =>
        val account = evt.document
        account.secondaryPrincipals.foreach(principal =>
          accountKeyDao.addAccountKey(principal.value, account.uuid)
        )
        Some(account.asInstanceOf[T])

      case evt: AccountActivatedEvent =>
        state.map(_
          .copyWithStatus(AccountStatus.Active)
          .copyWithVerificationToken(None)
          .copyWithLastUpdated(evt.lastUpdated)
          .asInstanceOf[T]
        )

      case evt: AccountDisabledEvent =>
        import evt._
        state.map(_
          .copyWithStatus(AccountStatus.Disabled)
          .copyWithNbLoginFailures(nbLoginFailures)
          .copyWithLastUpdated(lastUpdated)
          .asInstanceOf[T]
        )

      case evt: AccountDeletedEvent =>
        state.map(_
          .copyWithStatus(AccountStatus.Deleted)
          .copyWithLastUpdated(evt.lastUpdated)
          .asInstanceOf[T]
        )

      case _: AccountDestroyedEvent =>
        state match {
          case Some(account) =>
            account.principals.foreach(principal =>
              accountKeyDao.removeAccountKey(principal.value)
            )
          case _ =>
        }
        emptyState

      case evt: LoginUpdatedEvent =>
        import evt._
        state match {
          case Some(account) =>
            account.secondaryPrincipals.foreach(principal =>
              accountKeyDao.removeAccountKey(principal.value)
            )
            val updatedAccount = account.add(principal)
            updatedAccount.secondaryPrincipals.foreach(principal =>
              accountKeyDao.addAccountKey(principal.value, uuid)
            )
            Some(updatedAccount
              .copyWithLastUpdated(lastUpdated)
              .asInstanceOf[T]
            )
          case _             => state
        }

      case evt: ProfileUpdatedEvent[_] =>
        import evt._
        state match {
          case Some(account) =>
            account.secondaryPrincipals.foreach(principal =>
              accountKeyDao.removeAccountKey(principal.value)
            )
            val updatedAccount = account.add(profile)
            updatedAccount.secondaryPrincipals.foreach(principal =>
              accountKeyDao.addAccountKey(principal.value, uuid)
            )
            Some(
              updatedAccount
                .copyWithLastUpdated(lastUpdated)
                .asInstanceOf[T]
            )
          case _             => state
        }

      case evt: DeviceRegisteredEvent =>
        import evt._
        state.map(account =>
          account.copyWithRegistrations(
            account.registrations
              .filterNot(_.deviceId.getOrElse("") == registration.deviceId.getOrElse(""))
              .filterNot(_.regId == registration.regId).+:(registration)
          ).copyWithLastUpdated(lastUpdated).asInstanceOf[T]
        )

      case evt: DeviceUnregisteredEvent =>
        import evt._
        state.map(account =>
          account.copyWithRegistrations(
            account.registrations.filterNot(_.regId == registration.regId)
          ).copyWithLastUpdated(lastUpdated).asInstanceOf[T]
        )

      case evt: VerificationTokenAdded =>
        import evt._
        state.map(_
          .copyWithVerificationToken(Some(token))
          .copyWithLastUpdated(lastUpdated)
          .asInstanceOf[T])

      case evt: VerificationCodeAdded =>
        import evt._
        state.map(_
          .copyWithVerificationCode(Some(code))
          .copyWithLastUpdated(lastUpdated)
          .asInstanceOf[T])

      case evt: ProfileSwitchedEvent =>
        import evt._
        state.map(_.setCurrentProfile(name)
          .copyWithLastUpdated(lastUpdated)
          .asInstanceOf[T])

      case evt: LoginSucceeded =>
        import evt._
        state.map(_
          .copyWithNbLoginFailures(0)// reset number of login failures
          .copyWithLastLogin(Some(lastLogin))
          .withLastUpdated(lastLogin)
          .asInstanceOf[T]
        )

      case evt: LoginFailed =>
        import evt._
        state.map(_
          .copyWithNbLoginFailures(nbLoginFailures)
          .copyWithLastUpdated(lastUpdated)
          .asInstanceOf[T]
        )

      case evt: PasswordUpdatedEvent =>
        import evt._
        state.map(_
          .copyWithCredentials(credentials)
          .copyWithVerificationCode(code)
          .copyWithVerificationToken(token)
          .copyWithStatus(AccountStatus.Active) //TODO check this
          .copyWithNbLoginFailures(0)
          .copyWithLastUpdated(lastUpdated)
          .asInstanceOf[T]
        )

      case evt: LogoutEvent =>
        import evt._
        state.map(_
          .withLastLogout(lastLogout)
          .withLastUpdated(lastLogout)
          .asInstanceOf[T]
        )


      case _ => super.handleEvent(state, event)
    }
  }

  private[this] def lookupAccount(key: String)(implicit system: ActorSystem[_]): Option[String] =
    accountKeyDao.lookupAccount(key) complete ()
}

trait BasicAccountBehavior extends AccountBehavior[BasicAccount, BasicAccountProfile] { self: Generator =>
  override protected def createAccount(entityId: String, cmd: SignUp): Option[BasicAccount] =
    BasicAccount(cmd, Some(entityId))

  override protected def createProfileUpdatedEvent(uuid: String, profile: BasicAccountProfile, loginUpdated: Option[Boolean]): BasicAccountProfileUpdatedEvent =
    BasicAccountProfileUpdatedEvent(uuid, profile, loginUpdated).withLastUpdated(now())

  override protected def createAccountCreatedEvent(account: BasicAccount): AccountCreatedEvent[BasicAccount] =
    BasicAccountCreatedEvent(account)
}

object BasicAccountBehavior extends BasicAccountBehavior
  with DefaultGenerator {
  override def persistenceId: String = "Account"
}

object MockBasicAccountBehavior extends BasicAccountBehavior with MockGenerator {
  override def persistenceId: String = "MockAccount"
}
