package app.softnetwork.persistence.auth.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.DefaultComplete
import app.softnetwork.persistence.service.Service
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import app.softnetwork.api.server._
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.persistence.auth.config.Settings
import app.softnetwork.persistence.auth.handlers.{AccountHandler, BasicAccountTypeKey, MockBasicAccountTypeKey}
import app.softnetwork.persistence.auth.message._
import app.softnetwork.persistence.auth.model._
import app.softnetwork.persistence.auth.serialization._
import org.softnetwork.session.model.Session
import app.softnetwork.session.service.SessionService
import org.json4s.jackson.Serialization
import org.json4s.{Formats, jackson}

/**
  * Created by smanciot on 23/04/2020.
  */
trait AccountService extends Service[AccountCommand, AccountCommandResult]
  with AccountHandler
  with SessionService
  with Directives
  with DefaultComplete
  with Json4sSupport
  with StrictLogging {_: CommandTypeKey[AccountCommand] =>

  import Session._

  import app.softnetwork.persistence._

  implicit def formats: Formats = securityFormats

  implicit def serialization: Serialization.type = jackson.Serialization

  val route: Route = {
    pathPrefix(Settings.Path){
      signUp ~
        principal ~
        login ~
        activate ~
        logout ~
        verificationCode ~
        resetPasswordToken ~
        resetPassword ~
        unsubscribe ~
        device ~
        password
    }
  }

  lazy val signUp: Route = path("signUp") {
    post {
      entity(as[SignUp]) { signUp =>
        // execute signUp
        run(generateUUID()/**#MOSA-454**/, signUp) completeWith {
          case r: AccountCreated =>
            lazy val completion = complete(HttpResponse(status = StatusCodes.Created, entity = r.account.view))
            if(!Settings.ActivationEnabled){
              val account = r.account
              // create a new session
              sessionToDirective(Session(account.uuid))(ec) {
                // create a new anti csrf token
                setNewCsrfToken(checkHeader) {
                  completion
                }
              }
            }
            else{
              completion
            }
          case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                          => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val activate: Route = path("activate") {
    get {
      entity(as[Activate]) { activate =>
        // execute activate
        run(activate.token, activate) completeWith {
          case r: AccountActivated =>
            val account = r.account
            // create a new session
            sessionToDirective(Session(account.uuid))(ec) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK, entity = account.view))
              }
            }
          case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                          => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val login: Route = path("login" | "signIn") {
    post {
      entity(as[Login]) { login =>
        // execute Login
        run(login.login, login) completeWith {
          case r: LoginSucceededResult =>
            val account = r.account
            // create a new session
            val session = Session(account.uuid/** FIXME login.refreshable **/)
            session += (Session.adminKey, account.isAdmin)
            sessionToDirective(session)(ec) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK, entity = account.view))
              }
            }
          case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.Unauthorized, entity = error))
          case _                          => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val logout: Route = path("logout" | "signOut") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        _requiredSession(ec) { session =>
          run(session.id, Logout) completeWith {
            case _: LogoutSucceeded.type    =>
              // invalidate session
              _invalidateSession(ec) {
                complete(HttpResponse(StatusCodes.OK, entity = Map[String, String]()))
              }
            case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _                          => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val unsubscribe: Route = path("unsubscribe") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        _requiredSession(ec) { session =>
          run(session.id, Unsubscribe(session.id)) completeWith {
            case r: AccountDeleted =>
              // invalidate session
              _invalidateSession(ec) {
                complete(HttpResponse(status = StatusCodes.OK, entity = r.account.view))
              }
            case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _                          => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val verificationCode: Route = path("verificationCode") {
    post {
      entity(as[SendVerificationCode]) { verificationCode =>
        run(verificationCode.principal/**#MOSA-454**/, verificationCode) completeWith {
          case _: VerificationCodeSent.type  =>
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(status = StatusCodes.OK))
            }
          case error: AccountErrorMessage   => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                            => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val resetPasswordToken: Route = pathPrefix("resetPasswordToken") {
    pathSuffix(Segment) { token =>
      get{
        run(token, CheckResetPasswordToken(token)) completeWith {
          case _: ResetPasswordTokenChecked.type  =>
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(status = StatusCodes.OK))
            }
          case error: AccountErrorMessage   => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                            => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    } ~ post {
      entity(as[SendResetPasswordToken]) { resetPasswordToken =>
        run(resetPasswordToken.principal/**#MOSA-454**/, resetPasswordToken) completeWith {
          case _: ResetPasswordTokenSent.type  => complete(HttpResponse(status = StatusCodes.OK))
          case error: AccountErrorMessage      => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                               => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val resetPassword: Route = path("resetPassword") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        entity(as[ResetPassword]) { reset =>
          run(reset.token, reset) completeWith {
            case r: PasswordReseted         =>
              // create a new session
              sessionToDirective(Session(r.uuid))(ec) {
                complete(HttpResponse(status = StatusCodes.OK))
              }
            case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _                          => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val device: Route = pathPrefix("device") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        post{
          entity(as[DeviceRegistration]){registration =>
            run(
              session.id,
              RegisterDevice(
                session.id,
                registration
              )
            ) completeWith {
              case DeviceRegistered => complete(HttpResponse(status = StatusCodes.OK))
              case AccountNotFound  => complete(HttpResponse(status = StatusCodes.NotFound))
              case _                => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        } ~
          pathPrefix(Segment) {regId =>
            delete{
              run(session.id, UnregisterDevice(session.id, regId)) completeWith {
                case DeviceUnregistered         => complete(HttpResponse(status = StatusCodes.OK))
                case AccountNotFound            => complete(HttpResponse(status = StatusCodes.NotFound))
                case DeviceRegistrationNotFound => complete(HttpResponse(status = StatusCodes.NotFound))
                case _                          => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
          }
      }
    }
  }

  lazy val password: Route = pathPrefix("password"){
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        post {
          entity(as[PasswordData]) {data =>
            import data._
            run(session.id, UpdatePassword(session.id, oldPassword, newPassword, confirmedPassword)) completeWith {
              case _: PasswordUpdated => complete(HttpResponse(status = StatusCodes.OK))
              case error: AccountErrorMessage =>
                complete(
                  HttpResponse(
                    StatusCodes.BadRequest,
                    entity = error
                  )
                )
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }

  lazy val principal: Route = path("principal"){
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        post {
          entity(as[UpdateLogin]) {login =>
            run(session.id, login) completeWith {
              case LoginUpdated => complete(HttpResponse(status = StatusCodes.OK))
              case error: AccountErrorMessage =>
                complete(
                  HttpResponse(
                    StatusCodes.BadRequest,
                    entity = error
                  )
                )
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }
}

trait BasicAccountService extends AccountService with BasicAccountTypeKey

trait MockBasicAccountService extends BasicAccountService with MockBasicAccountTypeKey

object BasicAccountService {
  def apply(asystem: ActorSystem[_]): BasicAccountService = {
    new BasicAccountService {
      override implicit def system: ActorSystem[_] = asystem
    }
  }
}

object MockBasicAccountService {
  def apply(asystem: ActorSystem[_]): MockBasicAccountService = {
    new MockBasicAccountService {
      override implicit def system: ActorSystem[_] = asystem
    }
  }
}