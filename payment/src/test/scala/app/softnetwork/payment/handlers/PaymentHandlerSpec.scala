package app.softnetwork.payment.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.payment.PaymentTestKit
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.model.PaymentAccount.User
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner.BirthPlace
import app.softnetwork.payment.model.{Address, BankAccount, CardPreRegistration, KycDocument, LegalUser, PaymentAccount, PaymentUser, UboDeclaration}
import org.scalatest.wordspec.AnyWordSpecLike

class PaymentHandlerSpec extends MockPaymentHandler with AnyWordSpecLike with PaymentTestKit {

  implicit lazy val system: ActorSystem[_] = typedSystem()

  val orderUuid = "order"

  val customerUuid = "customer"

  val sellerUuid = "seller"

  val vendorUuid = "vendor"

  var cardPreRegistration: CardPreRegistration = _

  var preAuthorizationId: String = _

  /** natural user */
  val firstName = "firstName"
  val lastName = "lastName"
  val birthday = "26/12/1972"
  val email = "demo@softnetwork.fr"
  val naturalUser: PaymentUser =
    PaymentUser.defaultInstance
      .withCustomer(customerUuid)
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
    .withLegalRepresentative(naturalUser.withSeller(sellerUuid))
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

  "Payment handler" must {
    "pre register card" in {
      ? (PreRegisterCard(
        orderUuid,
        naturalUser
      )) await {
        case cardPreRegistered: CardPreRegistered =>
          cardPreRegistration = cardPreRegistered.cardPreRegistration
          ? (LoadPaymentAccount(customerUuid)) await {
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
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "pre authorize card" in {
      ? (PreAuthorizeCard(
        orderUuid,
        customerUuid,
        5100,
        Some(cardPreRegistration)
      )) await {
        case result: PaymentRedirection =>
          val params = result.redirectUrl.split("\\?").last.split("&|=")
            .grouped(2)
            .map(a => (a(0), a(1)))
            .toMap
          preAuthorizationId = params.getOrElse("preAuthorizationId", "")
        case other => fail(other.toString)
      }
    }

    "update card pre authorization" in {
      ? (PreAuthorizeCardFor3DS(
        orderUuid,
        preAuthorizationId
      )) await {
        case cardPreAuthorized: CardPreAuthorized =>
          val transactionId = cardPreAuthorized.transactionId
          ? (LoadPaymentAccount(customerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.transactions.exists(t => t.transactionId == transactionId))
              assert(paymentAccount.card.map(_.firstName).getOrElse("") == firstName)
              assert(paymentAccount.card.map(_.lastName).getOrElse("") == lastName)
              assert(paymentAccount.card.map(_.birthday).getOrElse("") == birthday)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }

    }

    "not create bank account with wrong iban" in {
      ?(CreateOrUpdateBankAccount(
        sellerUuid,
        BankAccount(None, ownerName, ownerAddress, "", bic)
      )) await {
        case WrongIban =>
        case other => fail(other.toString)
      }
    }

    "not create bank account with wrong bic" in {
      ?(CreateOrUpdateBankAccount(
        sellerUuid,
        BankAccount(None, ownerName, ownerAddress, iban, "")
      )) await {
        case WrongBic =>
        case other => fail(other.toString)
      }
    }

    "create bank account with natural user" in {
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
        BankAccount(None, ownerName, ownerAddress, iban, bic),
        Some(User.NaturalUser(naturalUser.withSeller(sellerUuid)))
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(sellerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.bankAccountId).getOrElse("")
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "update bank account with natural user" in {
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.NaturalUser(naturalUser.withLastName("anotherLastName").withSeller(sellerUuid)))
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(sellerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.bankAccountId).getOrElse("")
              assert(sellerBankAccountId != previousBankAccountId)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "not update bank account with wrong siret" in {
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
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
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
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
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
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
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.LegalUser(legalUser)),
        Some(true)
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(sellerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 2)
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF))
              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.bankAccountId).getOrElse("")
              assert(sellerBankAccountId != previousBankAccountId)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "update bank account with business legal user" in {
      ? (CreateOrUpdateBankAccount(
        sellerUuid,
        BankAccount(
          Some(sellerBankAccountId),
          ownerName,
          ownerAddress,
          iban,
          bic
        ),
        Some(User.LegalUser(legalUser.withLegalUserType(LegalUser.LegalUserType.BUSINESS))),
        Some(true)
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(sellerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 4)
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF))
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION))
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION))
              assert(paymentAccount.getLegalUser.uboDeclarationRequired)
              assert(paymentAccount.getLegalUser.uboDeclaration.map(_.uboDeclarationId).isDefined)
              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.bankAccountId).getOrElse("")
              assert(sellerBankAccountId != previousBankAccountId)
              uboDeclarationId = paymentAccount.getLegalUser.uboDeclaration.map(_.uboDeclarationId).getOrElse("")
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "add document(s)" in {
      Seq(
        KycDocument.KycDocumentType.KYC_IDENTITY_PROOF,
        KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
        KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION,
        KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION
      ).foreach { documentType =>
        ? (AddKycDocument(sellerUuid, Seq.empty, documentType)) await {
          case _: KycDocumentAdded =>
            ? (LoadPaymentAccount(sellerUuid)) await {
              case result: PaymentAccountLoaded =>
                val paymentAccount = result.paymentAccount
                assert(
                  paymentAccount.documents
                    .find(_.documentType == documentType)
                    .exists(_.documentStatus == KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
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
      ).foreach { documentType =>
        ? (LoadKycDocumentStatus(sellerUuid, documentType)) await {
          case result: KycDocumentStatusLoaded =>
            ? (UpdateKycDocumentStatus(
              result.report.documentId,
              Some(validated)
            )) await {
              case _: KycDocumentStatusUpdated =>
                ? (LoadPaymentAccount(sellerUuid)) await {
                  case result: PaymentAccountLoaded =>
                    val paymentAccount = result.paymentAccount
                    assert(
                      paymentAccount.documents
                        .find(_.documentType == documentType)
                        .exists(_.documentStatus == validated)
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
      ?(CreateOrUpdateUbo(sellerUuid, ubo)) await {
        case _: UboCreatedOrUpdated =>
        case other => fail(other.toString)
      }
    }

    "ask for declaration validation" in {
      ?(ValidateUboDeclaration(sellerUuid)) await {
        case UboDeclarationAskedForValidation =>
          ? (GetUboDeclaration(sellerUuid)) await {
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
      ?(UpdateUboDeclarationStatus(
        uboDeclarationId, Some(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED))
      ) await {
        case UboDeclarationStatusUpdated =>
          ? (GetUboDeclaration(sellerUuid)) await {
            case result: UboDeclarationLoaded =>
              assert(
                result.declaration.status == UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED
              )
              ? (LoadPaymentAccount(sellerUuid)) await {
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

    "pay in / out with pre authorized card" in {
      ? (PreAuthorizeCard(
        orderUuid,
        customerUuid,
        100,
        Some(cardPreRegistration)
      )) await {
        case result: CardPreAuthorized =>
          val transactionId = result.transactionId
          preAuthorizationId = transactionId
          ? (PayInWithCardPreAuthorized(preAuthorizationId, sellerUuid)) await {
            case _: PaidIn =>
            case other => fail(other.toString)
          }
        case other =>  fail(other.toString)
      }
    }

    "pay in / out" in {
      ? (PayIn(orderUuid, customerUuid, 100, sellerUuid)) await {
        case _: PaidIn =>
          ? (PayOut(orderUuid, sellerUuid, 100)) await {
            case _: PaidOut =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "pay in / refund" in {
      ? (PayIn(orderUuid, customerUuid, 100, sellerUuid)) await {
        case result: PaidIn =>
          val payInTransactionId = result.transactionId
          ? (Refund(orderUuid, payInTransactionId, 101, "change my mind", initializedByClient = true)) await {
            case IllegalTransactionAmount => // 101 > 100
              ? (Refund(orderUuid, payInTransactionId, 50, "change my mind", initializedByClient = true)) await {
                case _: Refunded =>
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "transfer" in {
      ? (CreateOrUpdateBankAccount(
        vendorUuid,
        BankAccount(None, ownerName, ownerAddress, iban, bic),
        Some(User.NaturalUser(naturalUser.withVendor(vendorUuid)))
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(vendorUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              vendorBankAccountId = paymentAccount.bankAccount.flatMap(_.bankAccountId).getOrElse("")
              ? (Transfer(orderUuid, sellerUuid, vendorUuid, 50, 10)) await {
                case result: Transfered =>
                  assert(result.paidOutTransactionId.isDefined)
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "delete declaration" in {
      ?(DeleteUboDeclaration(sellerUuid)) await {
        case UboDeclarationDeleted =>
          ? (GetUboDeclaration(sellerUuid)) await {
            case UboDeclarationNotLoaded =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "delete bank account" in {
      ?(DeleteBankAccount(sellerUuid)) await {
        case BankAccountDeleted =>
          ? (LoadBankAccount(sellerUuid)) await {
            case BankAccountNotFound =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

  }
}
