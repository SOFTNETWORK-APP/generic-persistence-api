package app.softnetwork.payment.service

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.DefaultComplete
import app.softnetwork.payment.handlers.{GenericPaymentHandler, MockPaymentHandler, MangoPayPaymentHandler}
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.serialization._
import app.softnetwork.payment.config.Settings
import Settings.MangoPayConfig._
import akka.actor.typed.ActorSystem
import app.softnetwork.config.{Settings => CommonSettings}
import akka.http.javadsl.model.headers.{AcceptLanguage, UserAgent}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import app.softnetwork.persistence.message.ErrorMessage
import app.softnetwork.persistence.service.Service
import app.softnetwork.session.service.SessionService
import com.softwaremill.session.CsrfDirectives.randomTokenCsrfProtection
import com.softwaremill.session.CsrfOptions.checkHeader
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{Formats, jackson}
import org.json4s.jackson.Serialization
import org.softnetwork.session.model.Session
import app.softnetwork.api.server._
import app.softnetwork.payment.model._
import com.mangopay.core.enumerations.EventType

import java.io.ByteArrayOutputStream
import java.util.TimeZone
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

trait GenericPaymentService extends SessionService
  with Directives
  with DefaultComplete
  with Json4sSupport
  with StrictLogging
  with Service[PaymentCommand, PaymentResult] {_: GenericPaymentHandler =>

  implicit def serialization: Serialization.type = jackson.Serialization

  implicit def formats: Formats = paymentFormats

  import Session._

  def run(command: PaymentCommandWithKey)(implicit tTag: ClassTag[PaymentCommand]): Future[PaymentResult] =
    super.run(command.key, command)

  val route: Route = {
    pathPrefix(Settings.PaymentPath){
      hooks ~
        card ~
        `3ds` ~
        bank ~
        declaration ~
        kyc ~
        payment
    }
  }

  lazy val card: Route = pathPrefix("card"){
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get {
            run(LoadCards(session.id)) completeWith {
              case r: CardsLoaded =>
                complete(
                  HttpResponse(
                    StatusCodes.OK,
                    entity = r.cards
                  )
                )
              case r: CardsNotLoaded.type => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
              case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          } ~
          post {
            entity(as[PreRegisterCard]){ cmd =>
              run(cmd.copy(user = cmd.user.withUserId(session.id))) completeWith {
                case r: CardPreRegistered         =>
                  complete(
                    HttpResponse(
                      StatusCodes.OK,
                      entity = r.cardPreRegistration
                    )
                  )
                case r: CardNotPreRegistered.type => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
                case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                case _ => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
          } ~
            delete {
              parameter("cardId") { cardId =>
                run(DisableCard(session.id, cardId)) completeWith {
                  case _: CardDisabled.type => complete(HttpResponse(StatusCodes.OK))
                  case r: CardNotDisabled.type => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
                  case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                  case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                  case _ => complete(HttpResponse(StatusCodes.BadRequest))
                }
              }
            }
        }
      }
    }
  }

  lazy val payment: Route = {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get{
            run(LoadPaymentAccount(session.id)) completeWith {
              case r: PaymentAccountLoaded => complete(HttpResponse(StatusCodes.OK, entity = r.paymentAccount.view))
              case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          } ~
          post {
            optionalHeaderValueByType[AcceptLanguage]((): Unit) { language =>
              optionalHeaderValueByType[Accept]((): Unit) { acceptHeader =>
                optionalHeaderValueByType[UserAgent]((): Unit) { userAgent =>
                  extractClientIP { ipAddress =>
                    entity(as[CardPreAuthorization]) { cardPreAuthorization =>
                      import cardPreAuthorization._
                      val browserInfo =
                        if(language.isDefined &&
                          acceptHeader.isDefined &&
                          userAgent.isDefined &&
                          colorDepth.isDefined &&
                          screenWidth.isDefined &&
                          screenHeight.isDefined){
                          Some(
                            BrowserInfo.defaultInstance.copy(
                              colorDepth = colorDepth.get,
                              screenWidth = screenWidth.get,
                              screenHeight = screenHeight.get,
                              acceptHeader = acceptHeader.get.value(),
                              language = "fr-FR"/*language.get.value().replace('_', '-')*/,
                              timeZoneOffset = "+" + (TimeZone.getTimeZone("Europe/Paris").getRawOffset/(60*1000)),
                              userAgent = userAgent.get.value()
                            )
                          )
                        }
                        else{
                          var missingParameters: Set[String] = Set.empty
                          if(colorDepth.isEmpty)
                            missingParameters += "colorDepth"
                          if(screenWidth.isEmpty)
                            missingParameters += "screenWidth"
                          if(screenHeight.isEmpty)
                            missingParameters += "screenHeight"
                          if(missingParameters.nonEmpty)
                            logger.warn(s"Missing parameters ${missingParameters.mkString(", ")} will be mandatory")

                          var missingHeaders: Set[String] = Set.empty
                          if(language.isEmpty)
                            missingHeaders += "Accept-Language"
                          if(acceptHeader.isEmpty)
                            missingHeaders += "Accept"
                          if(userAgent.isEmpty)
                            missingHeaders += "User-Agent"
                          if(missingHeaders.nonEmpty)
                            logger.warn(s"Missing Http headers ${missingHeaders.mkString(", ")} will be mandatory")
                          None
                        }
                      run(
                        PreAuthorizeCard(
                          orderUuid,
                          session.id,
                          debitedAmount,
                          cardPreRegistration,
                          javaEnabled,
                          if(browserInfo.isDefined) Some(ipAddress) else None,
                          browserInfo
                        )
                      ) completeWith {
                        case r: CardPreAuthorized =>
                          complete(
                            HttpResponse(
                              StatusCodes.OK,
                              entity = r
                            )
                          )
                        case r: PaymentRedirection =>
                          complete(
                            HttpResponse(
                              StatusCodes.OK,
                              entity = r
                            )
                          )
                        case r: CardPreAuthorizationFailed => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
                        case r: CardNotPreAuthorized.type => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                        case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                        case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                        case other =>
                          logger.error(other.toString)
                          complete(HttpResponse(StatusCodes.BadRequest))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  lazy val `3ds`: Route = pathPrefix(secureModeRoute){
    pathPrefix(Segment) {orderUuid =>
      parameters("preAuthorizationId", "registerCard".as[Boolean]) {(preAuthorizationId, registerCard) =>
        run(PreAuthorizeCardFor3DS(orderUuid, preAuthorizationId, registerCard)) completeWith {
          case _: CardPreAuthorized =>
            complete(
              HttpResponse(
                StatusCodes.OK
              )
            )
          case r: PaymentRedirection      =>
            complete(
              HttpResponse(
                StatusCodes.OK,
                entity = r
              )
            )
          case r: CardPreAuthorizationFailed => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
          case r: CardNotPreAuthorized.type => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
          case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
          case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val hooks: Route = pathPrefix(hooksRoute){
    pathEnd{
      parameters("EventType", "RessourceId") {(eventType, ressourceId) =>
        Option(EventType.valueOf(eventType)) match {
          case Some(s) =>
            s match {
              case EventType.KYC_FAILED =>
                run(UpdateKycDocumentStatus(ressourceId, Some(KycDocument.KycDocumentStatus.KYC_DOCUMENT_REFUSED))) completeWith {
                  case _: KycDocumentStatusUpdated =>
                    logger.info(s"[Payment Hooks] KYC has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] KYC has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.KYC_SUCCEEDED =>
                run(UpdateKycDocumentStatus(ressourceId, Some(KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATED))) completeWith {
                  case _: KycDocumentStatusUpdated =>
                    logger.info(s"[Payment Hooks] KYC has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] KYC has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.KYC_OUTDATED =>
                run(UpdateKycDocumentStatus(ressourceId, Some(KycDocument.KycDocumentStatus.KYC_DOCUMENT_OUT_OF_DATE))) completeWith {
                  case _: KycDocumentStatusUpdated =>
                    logger.info(s"[Payment Hooks] KYC has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] KYC has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.UBO_DECLARATION_REFUSED =>
                run(UpdateUboDeclarationStatus(ressourceId, Some(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_REFUSED))) completeWith {
                  case UboDeclarationStatusUpdated =>
                    logger.info(s"[Payment Hooks] Ubo Declaration has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Ubo Declaration has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.UBO_DECLARATION_VALIDATED =>
                run(UpdateUboDeclarationStatus(ressourceId, Some(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED))) completeWith {
                  case UboDeclarationStatusUpdated =>
                    logger.info(s"[Payment Hooks] Ubo Declaration has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Ubo Declaration has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.UBO_DECLARATION_INCOMPLETE =>
                run(UpdateUboDeclarationStatus(ressourceId, Some(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_INCOMPLETE))) completeWith {
                  case UboDeclarationStatusUpdated =>
                    logger.info(s"[Payment Hooks] Ubo Declaration has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Ubo Declaration has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.USER_KYC_REGULAR =>
                run(ValidateRegularUser(ressourceId)) completeWith {
                  case RegularUserValidated =>
                    logger.info(s"[Payment Hooks] Regular User has been validated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Regular User has not been validated  for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case _ =>
                logger.error(s"[Payment Hooks] Event $eventType for $ressourceId is not supported")
                complete(HttpResponse(StatusCodes.BadRequest))
            }
          case None =>
            logger.error(s"[Payment Hooks] Event $eventType for $ressourceId is not supported")
            complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val bank: Route = pathPrefix("bank") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get {
            run(LoadBankAccount(session.id)) completeWith {
              case r: BankAccountLoaded        =>
                complete(
                  HttpResponse(
                    StatusCodes.OK,
                    entity = r.bankAccount.view
                  )
                )
              case r: BankAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case _                           => complete(HttpResponse(StatusCodes.BadRequest))
            }
          } ~
            post {
              entity(as[BankAccountCommand]) { bank =>
                import bank._
                run(CreateOrUpdateBankAccount(
                  session.id,
                  bankAccount,
                  user match {
                    case Left(naturalUser) => Some(PaymentAccount.User.NaturalUser(naturalUser))
                    case Right(legalUser) => Some(PaymentAccount.User.LegalUser(legalUser))
                  },
                  acceptedTermsOfPSP
                )) completeWith {
                  case _: BankAccountCreatedOrUpdated.type => complete(HttpResponse(StatusCodes.OK))
                  case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                  case _ => complete(HttpResponse(StatusCodes.BadRequest))
                }
              }
            } ~
            delete {
              run(DeleteBankAccount(session.id)) completeWith {
                case r: BankAccountDeleted.type  => complete(HttpResponse(StatusCodes.OK))
                case r: PaymentError => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case _ => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
        }
      }
    }
  }

  lazy val declaration: Route = pathPrefix("declaration") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get {
            run(GetUboDeclaration(session.id)) completeWith {
              case r: UboDeclarationLoaded => complete(HttpResponse(StatusCodes.OK, entity = r.declaration.view))
              case r: UboDeclarationNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          } ~ post {
            entity(as[UboDeclaration.UltimateBeneficialOwner]) { ubo =>
              run(CreateOrUpdateUbo(session.id, ubo)) completeWith {
                case r: UboCreatedOrUpdated => complete(HttpResponse(StatusCodes.OK, entity = r.ubo))
                case r: UboDeclarationNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case _ => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
          } ~ put {
            run(ValidateUboDeclaration(session.id)) completeWith {
              case _: UboDeclarationAskedForValidation.type => complete(HttpResponse(StatusCodes.OK))
              case r: UboDeclarationNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }

  lazy val kyc: Route = {
    pathPrefix("kyc"){
      parameter("documentType"){ documentType =>
        val maybeKycDocumentType: Option[KycDocument.KycDocumentType] =
          documentType match {
            case "KYC_IDENTITY_PROOF" => Some(KycDocument.KycDocumentType.KYC_IDENTITY_PROOF)
            case "KYC_REGISTRATION_PROOF" => Some(KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF)
            case "KYC_SHAREHOLDER_DECLARATION" => Some(KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION)
            case "KYC_ARTICLES_OF_ASSOCIATION" => Some(KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION)
            case _ => None
          }
        maybeKycDocumentType match {
          case None =>
            complete(HttpResponse(StatusCodes.BadRequest))
          case Some(kycDocumentType) =>
            // check anti CSRF token
            randomTokenCsrfProtection(checkHeader) {
              // check if a session exists
              _requiredSession(ec) { session =>
                pathEnd {
                  get {
                    run(LoadKycDocumentStatus(session.id, kycDocumentType)) completeWith {
                      case r: KycDocumentStatusLoaded => complete(HttpResponse(StatusCodes.OK, entity = r.report))
                      case r: KycDocumentStatusNotLoaded.type  => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                      case _ => complete(HttpResponse(StatusCodes.BadRequest))
                    }
                  } ~
                    post {
                      extractRequestContext { ctx =>
                        implicit val materializer: Materializer = ctx.materializer
                        fileUploadAll("pages"){
                          case files: Seq[(FileInfo, Source[ByteString, Any])] =>
                            val pages = for(file <- files) yield {
                              val bos = new ByteArrayOutputStream()
                              val future = file._2.map { s =>
                                bos.write(s.toArray)
                              }.runWith(Sink.ignore)
                              Await.result(future, CommonSettings.DefaultTimeout) // FIXME
                              val bytes = bos.toByteArray
                              bos.close()
                              bytes
                            }
                            run(AddKycDocument(session.id, pages, kycDocumentType)) completeWith {
                              case r: KycDocumentAdded => complete(HttpResponse(StatusCodes.OK, entity = r))
                              case r: KycDocumentNotAdded.type  => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                              case r: AcceptedTermsOfPSPRequired.type => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                              case _ => complete(HttpResponse(StatusCodes.BadRequest))
                            }
                          case _ => complete(HttpResponse(StatusCodes.BadRequest))
                        }
                      }
                    }
                }
              }
            }
        }
      }
    }
  }

}

trait MockPaymentService extends GenericPaymentService with MockPaymentHandler

object MockPaymentService {
  def apply(_system: ActorSystem[_]): MockPaymentService = {
    new MockPaymentService {
      override implicit def system: ActorSystem[_] = _system
    }
  }
}

trait MangoPayPaymentService extends GenericPaymentService with MangoPayPaymentHandler

object MangoPayPaymentService {
  def apply(_system: ActorSystem[_]): MangoPayPaymentService = {
    new MangoPayPaymentService {
      override implicit def system: ActorSystem[_] = _system
    }
  }
}