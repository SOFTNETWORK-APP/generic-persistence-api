package app.softnetwork.payment.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.model.PaymentAccount.User
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner.BirthPlace
import app.softnetwork.payment.model._
import app.softnetwork.payment.scalatest.PaymentTestKit
import app.softnetwork.time.{now => _, _}
import app.softnetwork.persistence.now
import org.scalatest.wordspec.AnyWordSpecLike

class PaymentHandlerSpec extends MockPaymentHandler with AnyWordSpecLike with PaymentTestKit {

  implicit lazy val system: ActorSystem[_] = typedSystem()

  val orderUuid = "order"

  val customerUuid = "customer"

  val sellerUuid = "seller"

  val vendorUuid = "vendor"

  var cardPreRegistration: CardPreRegistration = _

  var preAuthorizationId: String = _

  var recurringPaymentRegistrationId: String = _

  /** natural user */
  val firstName = "firstName"
  val lastName = "lastName"
  val birthday = "26/12/1972"
  val email = "demo@softnetwork.fr"
  val naturalUser: PaymentUser =
    PaymentUser.defaultInstance
      .withExternalUuid(customerUuid)
      .withFirstName(firstName)
      .withLastName(lastName)
      .withBirthday(birthday)
      .withEmail(email)


  /** bank account */
  var sellerBankAccountId: String = _
  var vendorBankAccountId: String = _
  val ownerName = s"$firstName $lastName"
  val ownerAddress: Address = Address.defaultInstance
    .withAddressLine("addressLine")
    .withCity("Paris")
    .withPostalCode("75002")
    .withCountry("FR")
  val iban = "FR1420041010050500013M02606"
  val bic = "SOGEFRPPPSZ"

  /** legal user */
  val siret = "12345678901234"
  val legalUser: LegalUser = LegalUser.defaultInstance
    .withSiret(siret)
    .withLegalName(ownerName)
    .withLegalUserType(LegalUser.LegalUserType.SOLETRADER)
    .withLegalRepresentative(naturalUser.withExternalUuid(sellerUuid))
    .withLegalRepresentativeAddress(ownerAddress)
    .withHeadQuartersAddress(ownerAddress)

  /** ultimate beneficial owner */
  val ubo: UltimateBeneficialOwner = UltimateBeneficialOwner.defaultInstance
    .withFirstName(firstName)
    .withLastName(lastName)
    .withBirthday(birthday)
    .withBirthPlace(BirthPlace.defaultInstance.withCity("city"))
    .withAddress(ownerAddress.addressLine)
    .withCity(ownerAddress.city)
    .withPostalCode(ownerAddress.postalCode)

  var uboDeclarationId: String = _

  var cardId: String = _

  var mandateId: String = _

  var directDebitTransactionId: String = _

  "Payment handler" must {
    "pre register card" in {
      !? (PreRegisterCard(
        orderUuid,
        naturalUser.withProfile("customer")
      )) await {
        case cardPreRegistered: CardPreRegistered =>
          cardPreRegistration = cardPreRegistered.cardPreRegistration
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(customerUuid, Some("customer")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              val naturalUser = paymentAccount.getNaturalUser
              assert(naturalUser.externalUuid == customerUuid)
              assert(naturalUser.firstName == firstName)
              assert(naturalUser.lastName == lastName)
              assert(naturalUser.birthday == birthday)
              assert(naturalUser.email == email)
              assert(naturalUser.userId.isDefined)
              assert(naturalUser.walletId.isDefined)
              assert(naturalUser.paymentUserType.getOrElse(PaymentUser.PaymentUserType.COLLECTOR).isPayer)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "pre authorize card" in {
      !? (PreAuthorizeCard(
        orderUuid,
        computeExternalUuidWithProfile(customerUuid, Some("customer")),
        5100,
        "EUR",
        Some(cardPreRegistration.id),
        Some(cardPreRegistration.preregistrationData),
        registerCard = true
      )) await {
        case result: PaymentRedirection =>
          val params = result.redirectUrl.split("\\?").last.split("[&=]")
            .grouped(2)
            .map(a => (a(0), a(1)))
            .toMap
          preAuthorizationId = params.getOrElse("preAuthorizationId", "")
        case other => fail(other.toString)
      }
    }

    "update card pre authorization" in {
      !? (PreAuthorizeCardFor3DS(
        orderUuid,
        preAuthorizationId
      )) await {
        case cardPreAuthorized: CardPreAuthorized =>
          val transactionId = cardPreAuthorized.transactionId
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(customerUuid, Some("customer")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.transactions.exists(t => t.id == transactionId))
              assert(paymentAccount.cards.size == 1)
              cardId = paymentAccount.cards.head.id
              assert(paymentAccount.cards.map(_.firstName).head == firstName)
              assert(paymentAccount.cards.map(_.lastName).head == lastName)
              assert(paymentAccount.cards.map(_.birthday).head == birthday)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "load cards" in {
      !? (LoadCards(computeExternalUuidWithProfile(customerUuid, Some("customer")))) await {
        case result: CardsLoaded =>
          val card = result.cards.find(_.id == cardId)
          assert(card.isDefined)
          assert(card.map(_.firstName).getOrElse("") == firstName)
          assert(card.map(_.lastName).getOrElse("") == lastName)
          assert(card.map(_.birthday).getOrElse("") == birthday)
          assert(card.exists(_.getActive))
        case other => fail(other.toString)
      }
    }

    "not create bank account with wrong iban" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(None, ownerName, ownerAddress, "", bic)
      )) await {
        case WrongIban =>
        case other => fail(other.toString)
      }
    }

    "not create bank account with wrong bic" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(None, ownerName, ownerAddress, iban, "")
      )) await {
        case WrongBic =>
        case other => fail(other.toString)
      }
    }

    "create bank account with natural user" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(None, ownerName, ownerAddress, iban, bic),
        Some(User.NaturalUser(naturalUser.withExternalUuid(sellerUuid).withProfile("seller")))
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.userCreated)
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
              assert(paymentAccount.getNaturalUser.paymentUserType.getOrElse(PaymentUser.PaymentUserType.PAYER).isCollector)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "update bank account with natural user" in {
      // update first name
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(
          User.NaturalUser(
            naturalUser
              .withFirstName("anotherFirstName")
              .withExternalUuid(sellerUuid).withProfile("seller")
          )
        )
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.kycUpdated && r.userUpdated && r.documentsUpdated)
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
//              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
//              assert(sellerBankAccountId != previousBankAccountId)
              assert(paymentAccount.getNaturalUser.paymentUserType.getOrElse(PaymentUser.PaymentUserType.PAYER).isCollector)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
      // update last name
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(
          User.NaturalUser(
            naturalUser
              .withFirstName("anotherFirstName")
              .withLastName("anotherLastName")
              .withExternalUuid(sellerUuid).withProfile("seller")
          )
        )
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.kycUpdated && r.userUpdated && r.documentsUpdated)
        case other => fail(other.toString)
      }
      // update birthday
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(
          User.NaturalUser(
            naturalUser
              .withFirstName("anotherFirstName")
              .withLastName("anotherLastName")
              .withBirthday("01/01/1980")
              .withExternalUuid(sellerUuid).withProfile("seller")
          )
        )
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.kycUpdated && r.userUpdated && r.documentsUpdated)
        case other => fail(other.toString)
      }
    }

    "update bank account except kyc information with natural user" in {
      // update country of residence
      !?(CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(
          User.NaturalUser(
            naturalUser
              .withFirstName("anotherFirstName")
              .withLastName("anotherLastName")
              .withBirthday("01/01/1980")
              .withCountryOfResidence("GA")
              .withExternalUuid(sellerUuid).withProfile("seller")
          )
        )
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.kycUpdated && !r.documentsUpdated && r.userUpdated)
      }
      // update nationality
      !?(CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(
          User.NaturalUser(
            naturalUser
              .withFirstName("anotherFirstName")
              .withLastName("anotherLastName")
              .withBirthday("01/01/1980")
              .withCountryOfResidence("GA")
              .withNationality("GA")
              .withExternalUuid(sellerUuid).withProfile("seller")
          )
        )
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.kycUpdated && !r.documentsUpdated && r.userUpdated)
      }
    }

    "not update bank account with wrong siret" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.LegalUser(legalUser.withSiret("")))
      )) await {
        case WrongSiret =>
        case other => fail(other.toString)
      }
    }

    "not update bank account with empty legal name" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.LegalUser(legalUser.withLegalName("")))
      )) await {
        case LegalNameRequired =>
        case other => fail(other.toString)
      }
    }

    "not update bank account without accepted terms of PSP" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.LegalUser(legalUser))
      )) await {
        case AcceptedTermsOfPSPRequired =>
        case other => fail(other.toString)
      }
    }

    "update bank account with sole trader legal user" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.LegalUser(legalUser.withLegalRepresentative(legalUser.legalRepresentative.withProfile("seller")))),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.userTypeUpdated && r.kycUpdated && r.documentsUpdated && r.userUpdated)
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 2)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF))
//              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
//              assert(sellerBankAccountId != previousBankAccountId)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "update bank account with business legal user" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(
          User.LegalUser(
            legalUser
              .withLegalUserType(LegalUser.LegalUserType.BUSINESS)
              .withLegalRepresentative(
                legalUser.legalRepresentative
                  .withProfile("seller")
              )
          )
        ),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.userTypeUpdated && r.kycUpdated && r.documentsUpdated && r.userUpdated)
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 4)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION))
              assert(paymentAccount.getLegalUser.uboDeclarationRequired)
              assert(paymentAccount.getLegalUser.uboDeclaration.map(_.id).isDefined)
//              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
//              assert(sellerBankAccountId != previousBankAccountId)
              uboDeclarationId = paymentAccount.getLegalUser.uboDeclaration.map(_.id).getOrElse("")
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "update bank account except kyc information with business legal user" in {
      var updatedBankAccount =
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        )
      var updatedLegalUser =
        legalUser
          .withLegalUserType(LegalUser.LegalUserType.BUSINESS)
          .withLegalRepresentative(
            legalUser.legalRepresentative
              .withProfile("seller")
          )
      // update bank account owner name
      updatedBankAccount = updatedBankAccount.withOwnerName("anotherOwnerName")
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        updatedBankAccount,
        Some(User.LegalUser(updatedLegalUser)),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.userTypeUpdated && !r.kycUpdated && !r.documentsUpdated && !r.userUpdated && r.bankAccountUpdated)
        case other => fail(other.toString)
      }
      // update bank account owner address
      updatedBankAccount = updatedBankAccount.withOwnerAddress(ownerAddress.withAddressLine("anotherAddressLine"))
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        updatedBankAccount,
        Some(User.LegalUser(updatedLegalUser)),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.userTypeUpdated && !r.kycUpdated && !r.documentsUpdated && !r.userUpdated && r.bankAccountUpdated)
        case other => fail(other.toString)
      }
      // update bank account iban
      updatedBankAccount = updatedBankAccount.withIban("FR8914508000308185764223C20")
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        updatedBankAccount,
        Some(User.LegalUser(updatedLegalUser)),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.userTypeUpdated && !r.kycUpdated && !r.documentsUpdated && !r.userUpdated && r.bankAccountUpdated)
        case other => fail(other.toString)
      }
      // update bank account bic
      updatedBankAccount = updatedBankAccount.withBic("AGFBFRCC")
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        updatedBankAccount,
        Some(User.LegalUser(updatedLegalUser)),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.userTypeUpdated && !r.kycUpdated && !r.documentsUpdated && !r.userUpdated && r.bankAccountUpdated)
        case other => fail(other.toString)
      }
      // update siret
      updatedLegalUser = updatedLegalUser.withSiret("12345678912345")
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(sellerUuid, Some("seller")),
        updatedBankAccount,
        Some(User.LegalUser(updatedLegalUser)),
        Some(true)
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(!r.userTypeUpdated && !r.documentsUpdated && r.userUpdated && !r.bankAccountUpdated)
        case other => fail(other.toString)
      }
    }

    "add document(s)" in {
      Seq(
        KycDocument.KycDocumentType.KYC_IDENTITY_PROOF,
        KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
        KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION,
        KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION
      ).foreach { `type` =>
        !? (AddKycDocument(computeExternalUuidWithProfile(sellerUuid, Some("seller")), Seq.empty, `type`)) await {
          case _: KycDocumentAdded =>
            !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
              case result: PaymentAccountLoaded =>
                val paymentAccount = result.paymentAccount
                assert(
                  paymentAccount.documents
                    .find(_.`type` == `type`)
                    .exists(_.status == KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
                )
              case other => fail(other.toString)
            }
          case other => fail(other.toString)
        }
      }
    }

    "update document(s) status" in {
      val validated = KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATED
      Seq(
        KycDocument.KycDocumentType.KYC_IDENTITY_PROOF,
        KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
        KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION,
        KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION
      ).foreach { `type` =>
        !? (LoadKycDocumentStatus(computeExternalUuidWithProfile(sellerUuid, Some("seller")), `type`)) await {
          case result: KycDocumentStatusLoaded =>
            !? (UpdateKycDocumentStatus(
              result.report.id,
              Some(validated)
            )) await {
              case _: KycDocumentStatusUpdated =>
                !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
                  case result: PaymentAccountLoaded =>
                    val paymentAccount = result.paymentAccount
                    assert(
                      paymentAccount.documents
                        .find(_.`type` == `type`)
                        .exists(_.status == validated)
                    )
                  case other => fail(other.toString)
                }
              case other => fail(other.toString)
            }
          case other => fail(other.toString)
        }
      }
    }

    "create or update ultimate beneficial owner" in {
      !? (CreateOrUpdateUbo(computeExternalUuidWithProfile(sellerUuid, Some("seller")), ubo)) await {
        case _: UboCreatedOrUpdated =>
        case other => fail(other.toString)
      }
    }

    "ask for declaration validation" in {
      !? (ValidateUboDeclaration(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
        case UboDeclarationAskedForValidation =>
          !? (GetUboDeclaration(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case result: UboDeclarationLoaded =>
              assert(
                result.declaration.status == UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATION_ASKED
              )
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "update declaration status" in {
      !? (UpdateUboDeclarationStatus(
        uboDeclarationId, Some(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED))
      ) await {
        case UboDeclarationStatusUpdated =>
          !? (GetUboDeclaration(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case result: UboDeclarationLoaded =>
              assert(
                result.declaration.status == UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED
              )
              !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
                case result: PaymentAccountLoaded =>
                  val paymentAccount = result.paymentAccount
                  assert(
                    paymentAccount.paymentAccountStatus == PaymentAccount.PaymentAccountStatus.COMPTE_OK
                  )
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "cancel pre authorized card" in {
      !? (PreAuthorizeCard(
        orderUuid,
        computeExternalUuidWithProfile(customerUuid, Some("customer")),
        100,
        "EUR",
        Some(cardPreRegistration.id),
        Some(cardPreRegistration.preregistrationData),
        registerCard = true
      )) await {
        case result: CardPreAuthorized =>
          val transactionId = result.transactionId
          preAuthorizationId = transactionId
          !? (CancelPreAuthorization(orderUuid, preAuthorizationId)) await {
            case result: PreAuthorizationCanceled =>
              assert(result.preAuthorizationCanceled)
            case other => fail(other.getClass)
          }
        case other => fail(other.getClass)
      }
    }

    "pay in / out with pre authorized card" in {
      !? (PreAuthorizeCard(
        orderUuid,
        computeExternalUuidWithProfile(customerUuid, Some("customer")),
        100,
        "EUR",
        Some(cardPreRegistration.id),
        Some(cardPreRegistration.preregistrationData),
        registerCard = true
      )) await {
        case result: CardPreAuthorized =>
          val transactionId = result.transactionId
          preAuthorizationId = transactionId
          !? (PayInWithCardPreAuthorized(preAuthorizationId, computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case _: PaidIn =>
              !? (PayOut(orderUuid, computeExternalUuidWithProfile(sellerUuid, Some("seller")), 100)) await {
                case _: PaidOut =>
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other =>  fail(other.toString)
      }
    }

    "pay in / out" in {
      !? (PayIn(orderUuid, computeExternalUuidWithProfile(customerUuid, Some("customer")), 100, "EUR", computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
        case _: PaidIn =>
          !? (PayOut(orderUuid, computeExternalUuidWithProfile(sellerUuid, Some("seller")), 100)) await {
            case _: PaidOut =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "pay in / refund" in {
      !? (PayIn(orderUuid, computeExternalUuidWithProfile(customerUuid, Some("customer")), 100, "EUR", computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
        case result: PaidIn =>
          val payInTransactionId = result.transactionId
          !? (Refund(orderUuid, payInTransactionId, 101, "EUR", "change my mind", initializedByClient = true)) await {
            case IllegalTransactionAmount => // 101 > 100
              !? (Refund(orderUuid, payInTransactionId, 50, "EUR", "change my mind", initializedByClient = true)) await {
                case _: Refunded =>
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "transfer" in {
      !? (CreateOrUpdateBankAccount(
        computeExternalUuidWithProfile(vendorUuid, Some("vendor")),
        BankAccount(None, ownerName, ownerAddress, iban, bic),
        Some(User.NaturalUser(naturalUser.withExternalUuid(vendorUuid).withProfile("vendor")))
      )) await {
        case r: BankAccountCreatedOrUpdated =>
          assert(r.userCreated)
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              vendorBankAccountId = paymentAccount.getBankAccount.getId
              !? (AddKycDocument(computeExternalUuidWithProfile(vendorUuid, Some("vendor")), Seq.empty, KycDocument.KycDocumentType.KYC_IDENTITY_PROOF)) await {
                case result: KycDocumentAdded =>
                  !? (UpdateKycDocumentStatus(
                    result.kycDocumentId,
                    Some(KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATED)
                  )) await {
                    case _: KycDocumentStatusUpdated =>
                    case other => fail(other.toString)
                  }
                case other => fail(other.toString)
              }
              !? (Transfer(Some(orderUuid), computeExternalUuidWithProfile(sellerUuid, Some("seller")), computeExternalUuidWithProfile(vendorUuid, Some("vendor")), 50, 10)) await {
                case result: Transfered =>
                  assert(result.paidOutTransactionId.isDefined)
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "create mandate" in {
      !? (CreateMandate(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
        case MandateCreated =>
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
            case result: PaymentAccountLoaded =>
              val bankAccount = result.paymentAccount.getBankAccount
              assert(bankAccount.mandateId.isDefined)
              mandateId = bankAccount.getMandateId
              assert(bankAccount.getMandateStatus == BankAccount.MandateStatus.MANDATE_SUBMITTED)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "direct debit" in {
      !? (DirectDebit(computeExternalUuidWithProfile(vendorUuid, Some("vendor")), 100, 0, "EUR", "Direct Debit")) await {
        case r: DirectDebited =>
          directDebitTransactionId = r.transactionId
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
            case result: PaymentAccountLoaded =>
              result.paymentAccount.transactions.find(_.id == r.transactionId) match {
                case Some(transaction) =>
                  assert(transaction.currency == "EUR")
                  assert(transaction.paymentType == Transaction.PaymentType.DIRECT_DEBITED)
                  assert(transaction.amount == 100)
                  assert(transaction.fees == 0)
                case _ => fail("transaction not found")
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "load direct debit status" in {
      !? (LoadDirectDebitTransaction(directDebitTransactionId)) await {
        case _: DirectDebited =>
        case other => fail(other.toString)
      }
    }

    "update mandate status" in {
      !? (UpdateMandateStatus(mandateId, Some(BankAccount.MandateStatus.MANDATE_ACTIVATED))) await {
        case r: MandateStatusUpdated =>
          val result = r.result
          assert(result.id == mandateId)
          assert(result.status == BankAccount.MandateStatus.MANDATE_ACTIVATED)
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
            case result: PaymentAccountLoaded =>
              val bankAccount = result.paymentAccount.getBankAccount
              assert(bankAccount.getMandateId == mandateId)
              assert(bankAccount.getMandateStatus == BankAccount.MandateStatus.MANDATE_ACTIVATED)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    val probe = createTestProbe[PaymentResult]()
    subscribeProbe(probe)

    "register recurring direct debit payment" in {
      !? (RegisterRecurringPayment(computeExternalUuidWithProfile(vendorUuid, Some("vendor")),
        `type` = RecurringPayment.RecurringPaymentType.DIRECT_DEBIT,
        frequency = Some(RecurringPayment.RecurringPaymentFrequency.DAILY),
        endDate = Some(now()),
        fixedNextAmount = Some(true),
        nextDebitedAmount = Some(1000),
        nextFeesAmount = Some(100)
      )) await {
        case result: RecurringPaymentRegistered =>
          recurringPaymentRegistrationId = result.recurringPaymentRegistrationId
          !? (LoadRecurringPayment(computeExternalUuidWithProfile(vendorUuid, Some("vendor")),
            recurringPaymentRegistrationId
          )) await {
            case result: RecurringPaymentLoaded =>
              val recurringPayment = result.recurringPayment
              assert(recurringPayment.`type`.isDirectDebit)
              assert(recurringPayment.getFrequency.isDaily)
              assert(recurringPayment.firstDebitedAmount == 0)
              assert(recurringPayment.firstFeesAmount == 0)
              assert(recurringPayment.getFixedNextAmount)
              assert(recurringPayment.getNextDebitedAmount == 1000)
              assert(recurringPayment.getNextFeesAmount == 100)
              assert(recurringPayment.getNumberOfRecurringPayments == 0)
              assert(recurringPayment.getNextRecurringPaymentDate.isEqual(now()))
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "execute direct debit automatically for next recurring payment" in{
      !? (CancelMandate(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
        case MandateNotCanceled =>
        case other => fail(other.toString)
      }
      probe.expectMessageType[Schedule4PaymentTriggered]
      !? (LoadRecurringPayment(computeExternalUuidWithProfile(vendorUuid, Some("vendor")),
        recurringPaymentRegistrationId
      )) await {
        case result: RecurringPaymentLoaded =>
          val recurringPayment = result.recurringPayment
          assert(recurringPayment.getCumulatedDebitedAmount == 1000)
          assert(recurringPayment.getCumulatedFeesAmount == 100)
          assert(recurringPayment.getLastRecurringPaymentDate.isEqual(now()))
          assert(recurringPayment.lastRecurringPaymentTransactionId.isDefined)
          assert(recurringPayment.getNumberOfRecurringPayments == 1)
          assert(recurringPayment.nextRecurringPaymentDate.isEmpty)
        case other => fail(other.toString)
      }
    }

    "register recurring card payment" in {
      !? (RegisterRecurringPayment(computeExternalUuidWithProfile(customerUuid, Some("customer")),
        `type` = RecurringPayment.RecurringPaymentType.CARD,
        frequency = Some(RecurringPayment.RecurringPaymentFrequency.DAILY),
        endDate = Some(now().plusDays(1)),
        fixedNextAmount = Some(true),
        nextDebitedAmount = Some(1000),
        nextFeesAmount = Some(100)
      )) await {
        case result: RecurringPaymentRegistered =>
          recurringPaymentRegistrationId = result.recurringPaymentRegistrationId
          !? (LoadRecurringPayment(computeExternalUuidWithProfile(customerUuid, Some("customer")),
            recurringPaymentRegistrationId
          )) await {
            case result: RecurringPaymentLoaded =>
              val recurringPayment = result.recurringPayment
              assert(recurringPayment.`type`.isCard)
              assert(recurringPayment.getFrequency.isDaily)
              assert(recurringPayment.firstDebitedAmount == 0)
              assert(recurringPayment.firstFeesAmount == 0)
              assert(recurringPayment.getFixedNextAmount)
              assert(recurringPayment.getNextDebitedAmount == 1000)
              assert(recurringPayment.getNextFeesAmount == 100)
              assert(recurringPayment.getNumberOfRecurringPayments == 0)
              assert(recurringPayment.getCardStatus.isCreated)
              assert(recurringPayment.getNextRecurringPaymentDate.isEqual(now()))
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "execute first recurring card payment" in {
      !? (PayInFirstRecurring(recurringPaymentRegistrationId, computeExternalUuidWithProfile(customerUuid, Some("customer")))) await {
        case _: FirstRecurringPaidIn =>
          !? (LoadRecurringPayment(computeExternalUuidWithProfile(customerUuid, Some("customer")),
            recurringPaymentRegistrationId
          )) await {
            case result: RecurringPaymentLoaded =>
              val recurringPayment = result.recurringPayment
              assert(recurringPayment.`type`.isCard)
              assert(recurringPayment.getFrequency.isDaily)
              assert(recurringPayment.firstDebitedAmount == 0)
              assert(recurringPayment.firstFeesAmount == 0)
              assert(recurringPayment.getFixedNextAmount)
              assert(recurringPayment.getNextDebitedAmount == 1000)
              assert(recurringPayment.getNextFeesAmount == 100)
              assert(recurringPayment.getNumberOfRecurringPayments == 1)
              assert(recurringPayment.getCardStatus.isInProgress)
              assert(recurringPayment.getNextRecurringPaymentDate.isEqual(now().plusDays(1)))
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "cancel mandate" in {
      !? (CancelMandate(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
        case MandateCanceled =>
          !? (LoadPaymentAccount(computeExternalUuidWithProfile(vendorUuid, Some("vendor")))) await {
            case result: PaymentAccountLoaded =>
              val bankAccount = result.paymentAccount.getBankAccount
              assert(bankAccount.mandateId.isEmpty)
              assert(bankAccount.mandateStatus.isEmpty)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "create or update payment account" in {
      !? (LoadPaymentAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
        case result: PaymentAccountLoaded =>
          val paymentAccount = result.paymentAccount
          val legalUser = paymentAccount.getLegalUser
          val externalUuid = "other"
          val profile = "other"
          !? (
            CreateOrUpdatePaymentAccount(
              paymentAccount.withLegalUser(
                legalUser.withLegalRepresentative(legalUser.legalRepresentative.withExternalUuid(externalUuid).withProfile(profile))
              )
            )
          ) await {
            case PaymentAccountCreated =>
              !? (LoadPaymentAccount(computeExternalUuidWithProfile(externalUuid, Some(profile)))) await {
                case result: PaymentAccountLoaded =>
                  logger.info(result.paymentAccount.toProtoString)
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "delete bank account" in {
      !? (DeleteBankAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
        case BankAccountDeleted =>
          !? (LoadBankAccount(computeExternalUuidWithProfile(sellerUuid, Some("seller")))) await {
            case BankAccountNotFound =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "disable card" in {
      !? (DisableCard(computeExternalUuidWithProfile(customerUuid, Some("customer")), cardId)) await {
        case CardNotDisabled => // card associated with recurring payment not ended
        case other => fail(other.toString)
      }
      !? (UpdateRecurringCardPaymentRegistration(
        computeExternalUuidWithProfile(customerUuid, Some("customer")),
        recurringPaymentRegistrationId,
        status = Some(RecurringPayment.RecurringCardPaymentStatus.ENDED))
      ) await {
        case _: RecurringCardPaymentRegistrationUpdated =>
        case other => fail(other.toString)
      }
      !? (DisableCard(computeExternalUuidWithProfile(customerUuid, Some("customer")), cardId)) await {
        case CardDisabled =>
          !? (LoadCards(computeExternalUuidWithProfile(customerUuid, Some("customer")))) await {
            case result: CardsLoaded =>
              val card = result.cards.find(_.id == cardId)
              assert(card.isDefined)
              assert(!card.exists(_.getActive))
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }
  }
}
