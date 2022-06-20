package app.softnetwork.payment.service

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.DefaultComplete
import app.softnetwork.payment.handlers.{GenericPaymentHandler, MangoPayPaymentHandler, MockPaymentHandler}
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.serialization._
import app.softnetwork.payment.config.Settings
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
import app.softnetwork.payment.config.Settings._
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
        payInFor3ds ~
        preAuthorizeCardFor3ds ~
        bank ~
        declaration ~
        kyc ~
        mandate ~
        payment
    }
  }

  lazy val card: Route = pathPrefix(CardRoute){
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get {
            run(LoadCards(externalUuidWithProfile(session))) completeWith {
              case r: CardsLoaded =>
                complete(
                  HttpResponse(
                    StatusCodes.OK,
                    entity = r.cards.map(_.view)
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
              var updatedUser =
                if(cmd.user.externalUuid.trim.isEmpty){
                  cmd.user.withExternalUuid(session.id)
                }
                else{
                  cmd.user
                }
              session.profile match {
                case Some(profile) if updatedUser.profile.isEmpty =>
                  updatedUser = updatedUser.withProfile(profile)
                case _ =>
              }
              run(cmd.copy(user = updatedUser)) completeWith {
                case r: CardPreRegistered =>
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
                run(DisableCard(externalUuidWithProfile(session), cardId)) completeWith {
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
        get {
          pathEnd {
            run(LoadPaymentAccount(externalUuidWithProfile(session))) completeWith {
              case r: PaymentAccountLoaded => complete(HttpResponse(StatusCodes.OK, entity = r.paymentAccount.view))
              case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        } ~
          post {
            optionalHeaderValueByType[AcceptLanguage]((): Unit) { language =>
              optionalHeaderValueByType[Accept]((): Unit) { acceptHeader =>
                optionalHeaderValueByType[UserAgent]((): Unit) { userAgent =>
                  extractClientIP { ipAddress =>
                      entity(as[Payment]) { payment =>
                        import payment._
                        val browserInfo =
                          if (language.isDefined &&
                            acceptHeader.isDefined &&
                            userAgent.isDefined &&
                            colorDepth.isDefined &&
                            screenWidth.isDefined &&
                            screenHeight.isDefined) {
                            Some(
                              BrowserInfo.defaultInstance.copy(
                                colorDepth = colorDepth.get,
                                screenWidth = screenWidth.get,
                                screenHeight = screenHeight.get,
                                acceptHeader = acceptHeader.get.value(),
                                javaEnabled = javaEnabled,
                                javascriptEnabled = javascriptEnabled,
                                language = "fr-FR" /*language.get.value().replace('_', '-')*/ ,
                                timeZoneOffset = "+" + (TimeZone.getTimeZone("Europe/Paris").getRawOffset / (60 * 1000)),
                                userAgent = userAgent.get.value()
                              )
                            )
                          }
                          else {
                            var missingParameters: Set[String] = Set.empty
                            if (colorDepth.isEmpty)
                              missingParameters += "colorDepth"
                            if (screenWidth.isEmpty)
                              missingParameters += "screenWidth"
                            if (screenHeight.isEmpty)
                              missingParameters += "screenHeight"
                            if (missingParameters.nonEmpty)
                              logger.warn(s"Missing parameters ${missingParameters.mkString(", ")} will be mandatory")

                            var missingHeaders: Set[String] = Set.empty
                            if (language.isEmpty)
                              missingHeaders += "Accept-Language"
                            if (acceptHeader.isEmpty)
                              missingHeaders += "Accept"
                            if (userAgent.isEmpty)
                              missingHeaders += "User-Agent"
                            if (missingHeaders.nonEmpty)
                              logger.warn(s"Missing Http headers ${missingHeaders.mkString(", ")} will be mandatory")
                            None
                          }
                        pathPrefix(PreAuthorizeCardRoute) {
                          run(
                            PreAuthorizeCard(
                              orderUuid,
                              externalUuidWithProfile(session),
                              debitedAmount,
                              currency,
                              registrationId,
                              registrationData,
                              registerCard,
                              if (browserInfo.isDefined) Some(ipAddress) else None,
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
                            case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                            case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                            case other =>
                              logger.error(other.toString)
                              complete(HttpResponse(StatusCodes.BadRequest))
                          }
                        } ~ pathPrefix(PayInRoute) {
                          parameters("creditedAccount") { creditedAccount: String =>
                            run(
                              PayIn(
                                orderUuid,
                                externalUuidWithProfile(session),
                                debitedAmount,
                                currency,
                                creditedAccount,
                                registrationId,
                                registrationData,
                                registerCard,
                                if (browserInfo.isDefined) Some(ipAddress) else None,
                                browserInfo,
                                statementDescriptor,
                                paymentType
                              )
                            ) completeWith {
                              case r: PaidIn =>
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
                              case r: PayInFailed => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
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
  }

  lazy val payInFor3ds: Route = pathPrefix(SecureModeRoute/PayInRoute){
    pathPrefix(Segment) {orderUuid =>
      parameters("transactionId", "registerCard".as[Boolean]) {(transactionId, registerCard) =>
        run(PayInFor3DS(orderUuid, transactionId, registerCard)) completeWith {
          case r: PaidIn =>
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
          case r: PayInFailed => complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
          case r: TransactionNotFound.type => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
          case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
          case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val preAuthorizeCardFor3ds: Route = pathPrefix(SecureModeRoute/PreAuthorizeCardRoute){
    pathPrefix(Segment) {orderUuid =>
      parameters("preAuthorizationId", "registerCard".as[Boolean]) {(preAuthorizationId, registerCard) =>
        run(PreAuthorizeCardFor3DS(orderUuid, preAuthorizationId, registerCard)) completeWith {
          case _: CardPreAuthorized =>
            complete(
              HttpResponse(
                StatusCodes.OK
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
          case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
          case r: ErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val hooks: Route = pathPrefix(HooksRoute){
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
                    logger.warn(s"[Payment Hooks] Regular User has not been validated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.MANDATE_FAILED =>
                run(UpdateMandateStatus(ressourceId, Some(BankAccount.MandateStatus.MANDATE_FAILED))) completeWith {
                  case _: MandateStatusUpdated =>
                    logger.info(s"[Payment Hooks] Mandate has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Mandate has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.MANDATE_SUBMITTED =>
                run(UpdateMandateStatus(ressourceId, Some(BankAccount.MandateStatus.MANDATE_SUBMITTED))) completeWith {
                  case _: MandateStatusUpdated =>
                    logger.info(s"[Payment Hooks] Mandate has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Mandate has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.MANDATE_CREATED =>
                run(UpdateMandateStatus(ressourceId, Some(BankAccount.MandateStatus.MANDATE_CREATED))) completeWith {
                  case _: MandateStatusUpdated =>
                    logger.info(s"[Payment Hooks] Mandate has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Mandate has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.MANDATE_EXPIRED =>
                run(UpdateMandateStatus(ressourceId, Some(BankAccount.MandateStatus.MANDATE_EXPIRED))) completeWith {
                  case _: MandateStatusUpdated =>
                    logger.info(s"[Payment Hooks] Mandate has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Mandate has not been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                }
              case EventType.MANDATE_ACTIVATED =>
                run(UpdateMandateStatus(ressourceId, Some(BankAccount.MandateStatus.MANDATE_ACTIVATED))) completeWith {
                  case _: MandateStatusUpdated =>
                    logger.info(s"[Payment Hooks] Mandate has been updated for $ressourceId -> $eventType")
                    complete(HttpResponse(StatusCodes.OK))
                  case _ =>
                    logger.warn(s"[Payment Hooks] Mandate has not been updated for $ressourceId -> $eventType")
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

  lazy val bank: Route = pathPrefix(BankRoute) {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get {
            run(LoadBankAccount(externalUuidWithProfile(session))) completeWith {
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
                var externalUuid: String = ""
                val updatedUser: Option[PaymentAccount.User] =
                  user match {
                    case Left(naturalUser) =>
                      var updatedNaturalUser = {
                        if(naturalUser.externalUuid.trim.isEmpty){
                          naturalUser.withExternalUuid(session.id)
                        }
                        else {
                          naturalUser
                        }
                      }
                      session.profile match {
                        case Some(profile) if updatedNaturalUser.profile.isEmpty =>
                          updatedNaturalUser = updatedNaturalUser.withProfile(profile)
                        case _ =>
                      }
                      externalUuid = updatedNaturalUser.externalUuid
                      Some(PaymentAccount.User.NaturalUser(updatedNaturalUser))
                    case Right(legalUser) =>
                      var updatedLegalRepresentative = legalUser.legalRepresentative
                      if(updatedLegalRepresentative.externalUuid.trim.isEmpty){
                        updatedLegalRepresentative = updatedLegalRepresentative.withExternalUuid(session.id)
                      }
                      session.profile match {
                        case Some(profile) if updatedLegalRepresentative.profile.isEmpty =>
                          updatedLegalRepresentative = updatedLegalRepresentative.withProfile(profile)
                        case _ =>
                      }
                      externalUuid = updatedLegalRepresentative.externalUuid
                      Some(PaymentAccount.User.LegalUser(legalUser.withLegalRepresentative(updatedLegalRepresentative)))
                  }
                run(
                  CreateOrUpdateBankAccount(
                    externalUuidWithProfile(session),
                    bankAccount.withExternalUuid(externalUuid),
                    updatedUser,
                    acceptedTermsOfPSP
                  )
                ) completeWith {
                  case _: BankAccountCreatedOrUpdated.type => complete(HttpResponse(StatusCodes.OK))
                  case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                  case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                  case _ => complete(HttpResponse(StatusCodes.BadRequest))
                }
              }
            } ~
            delete {
              run(DeleteBankAccount(externalUuidWithProfile(session))) completeWith {
                case _: BankAccountDeleted.type  => complete(HttpResponse(StatusCodes.OK))
                case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case r: PaymentError => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case _ => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
        }
      }
    }
  }

  lazy val declaration: Route = pathPrefix(DeclarationRoute) {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        pathEnd {
          get {
            run(GetUboDeclaration(externalUuidWithProfile(session))) completeWith {
              case r: UboDeclarationLoaded => complete(HttpResponse(StatusCodes.OK, entity = r.declaration.view))
              case r: UboDeclarationNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          } ~ post {
            entity(as[UboDeclaration.UltimateBeneficialOwner]) { ubo =>
              run(CreateOrUpdateUbo(externalUuidWithProfile(session), ubo)) completeWith {
                case r: UboCreatedOrUpdated => complete(HttpResponse(StatusCodes.OK, entity = r.ubo))
                case r: UboDeclarationNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                case _ => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
          } ~ put {
            run(ValidateUboDeclaration(externalUuidWithProfile(session))) completeWith {
              case _: UboDeclarationAskedForValidation.type => complete(HttpResponse(StatusCodes.OK))
              case r: UboDeclarationNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
              case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }

  lazy val kyc: Route = {
    pathPrefix(KycRoute){
      parameter("documentType"){ documentType =>
        val maybeKycDocumentType: Option[KycDocument.KycDocumentType] =
          KycDocument.KycDocumentType.enumCompanion.fromName(documentType)
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
                    run(LoadKycDocumentStatus(externalUuidWithProfile(session), kycDocumentType)) completeWith {
                      case r: KycDocumentStatusLoaded => complete(HttpResponse(StatusCodes.OK, entity = r.report))
                      case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                      case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
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
                            run(AddKycDocument(externalUuidWithProfile(session), pages, kycDocumentType)) completeWith {
                              case r: KycDocumentAdded => complete(HttpResponse(StatusCodes.OK, entity = r))
                              case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                              case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
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

  lazy val mandate: Route =
    pathPrefix(MandateRoute) {
      parameter("MandateId") { mandateId =>
        run(UpdateMandateStatus(mandateId)) completeWith{
          case r: MandateStatusUpdated => complete(HttpResponse(StatusCodes.OK, entity = r.result))
          case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
          case _ => complete(HttpResponse(StatusCodes.BadRequest))
        }
      } ~
        // check anti CSRF token
        randomTokenCsrfProtection(checkHeader) {
          // check if a session exists
          _requiredSession(ec) { session =>
            post {
              run(CreateMandate(externalUuidWithProfile(session))) completeWith {
                case r: MandateConfirmationRequired => complete(HttpResponse(StatusCodes.OK, entity = r))
                case MandateCreated => complete(HttpResponse(StatusCodes.OK))
                case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                case _ => complete(HttpResponse(StatusCodes.BadRequest))
              }
            } ~
              delete {
                run(CancelMandate(externalUuidWithProfile(session))) completeWith {
                  case MandateCanceled => complete(HttpResponse(StatusCodes.OK))
                  case r: PaymentAccountNotFound.type => complete(HttpResponse(StatusCodes.NotFound, entity = r))
                  case r: PaymentError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                  case _ => complete(HttpResponse(StatusCodes.BadRequest))
                }
              }
          }
        }
    }

  protected[payment] def externalUuidWithProfile(session: Session): String =
    computeExternalUuidWithProfile(session.id, session.profile)
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