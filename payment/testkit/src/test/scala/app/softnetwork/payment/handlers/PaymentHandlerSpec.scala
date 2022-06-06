package app.softnetwork.payment.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.model.PaymentAccount.User
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner.BirthPlace
import app.softnetwork.payment.model._
import app.softnetwork.payment.scalatest.PaymentTestKit
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
        "EUR",
        Some(cardPreRegistration.id),
        Some(cardPreRegistration.preregistrationData),
        registerCard = true
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
      ?(LoadCards(customerUuid)) await {
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
        Some(User.NaturalUser(naturalUser.withExternalUuid(sellerUuid)))
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(sellerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
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
        Some(User.NaturalUser(naturalUser.withLastName("anotherLastName").withExternalUuid(sellerUuid)))
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(sellerUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
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
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF))
              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
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
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION))
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION))
              assert(paymentAccount.getLegalUser.uboDeclarationRequired)
              assert(paymentAccount.getLegalUser.uboDeclaration.map(_.id).isDefined)
              val previousBankAccountId = sellerBankAccountId
              sellerBankAccountId = paymentAccount.bankAccount.flatMap(_.id).getOrElse("")
              assert(sellerBankAccountId != previousBankAccountId)
              uboDeclarationId = paymentAccount.getLegalUser.uboDeclaration.map(_.id).getOrElse("")
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
      ).foreach { `type` =>
        ? (AddKycDocument(sellerUuid, Seq.empty, `type`)) await {
          case _: KycDocumentAdded =>
            ? (LoadPaymentAccount(sellerUuid)) await {
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
        ? (LoadKycDocumentStatus(sellerUuid, `type`)) await {
          case result: KycDocumentStatusLoaded =>
            ? (UpdateKycDocumentStatus(
              result.report.id,
              Some(validated)
            )) await {
              case _: KycDocumentStatusUpdated =>
                ? (LoadPaymentAccount(sellerUuid)) await {
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
        "EUR",
        Some(cardPreRegistration.id),
        Some(cardPreRegistration.preregistrationData),
        registerCard = true
      )) await {
        case result: CardPreAuthorized =>
          val transactionId = result.transactionId
          preAuthorizationId = transactionId
          ? (PayInWithCardPreAuthorized(preAuthorizationId, sellerUuid)) await {
            case _: PaidIn =>
              ? (PayOut(orderUuid, sellerUuid, 100)) await {
                case _: PaidOut =>
                case other => fail(other.toString)
              }
            case other => fail(other.toString)
          }
        case other =>  fail(other.toString)
      }
    }

    "pay in / out" in {
      ? (PayIn(orderUuid, customerUuid, 100, "EUR", sellerUuid)) await {
        case _: PaidIn =>
          ? (PayOut(orderUuid, sellerUuid, 100)) await {
            case _: PaidOut =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "pay in / refund" in {
      ? (PayIn(orderUuid, customerUuid, 100, "EUR", sellerUuid)) await {
        case result: PaidIn =>
          val payInTransactionId = result.transactionId
          ? (Refund(orderUuid, payInTransactionId, 101, "EUR", "change my mind", initializedByClient = true)) await {
            case IllegalTransactionAmount => // 101 > 100
              ? (Refund(orderUuid, payInTransactionId, 50, "EUR", "change my mind", initializedByClient = true)) await {
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
        Some(User.NaturalUser(naturalUser.withExternalUuid(vendorUuid)))
      )) await {
        case BankAccountCreatedOrUpdated =>
          ? (LoadPaymentAccount(vendorUuid)) await {
            case result: PaymentAccountLoaded =>
              val paymentAccount = result.paymentAccount
              assert(paymentAccount.bankAccount.isDefined)
              assert(paymentAccount.documents.size == 1)
              assert(paymentAccount.documents.exists(_.`type` == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF))
              vendorBankAccountId = paymentAccount.getBankAccount.getId
              ? (AddKycDocument(vendorUuid, Seq.empty, KycDocument.KycDocumentType.KYC_IDENTITY_PROOF)) await {
                case result: KycDocumentAdded =>
                  ?(UpdateKycDocumentStatus(
                    result.kycDocumentId,
                    Some(KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATED)
                  )) await {
                    case _: KycDocumentStatusUpdated =>
                    case other => fail(other.toString)
                  }
                case other => fail(other.toString)
              }
              ? (Transfer(Some(orderUuid), sellerUuid, vendorUuid, 50, 10)) await {
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
      ? (CreateMandate(vendorUuid)) await {
        case MandateCreated =>
          ? (LoadPaymentAccount(vendorUuid)) await {
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
      ? (DirectDebit(vendorUuid, 100, 0, "EUR", "Direct Debit")) await {
        case r: DirectDebited =>
          ? (LoadPaymentAccount(vendorUuid)) await {
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

    "update mandate status" in {
      ? (UpdateMandateStatus(mandateId, Some(BankAccount.MandateStatus.MANDATE_ACTIVATED))) await {
        case r: MandateStatusUpdated =>
          val result = r.result
          assert(result.id == mandateId)
          assert(result.status == BankAccount.MandateStatus.MANDATE_ACTIVATED)
          ? (LoadPaymentAccount(vendorUuid)) await {
            case result: PaymentAccountLoaded =>
              val bankAccount = result.paymentAccount.getBankAccount
              assert(bankAccount.getMandateId == mandateId)
              assert(bankAccount.getMandateStatus == BankAccount.MandateStatus.MANDATE_ACTIVATED)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "cancel mandate" in {
      ? (CancelMandate(vendorUuid)) await {
        case MandateCanceled =>
          ? (LoadPaymentAccount(vendorUuid)) await {
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
      ? (LoadPaymentAccount(vendorUuid)) await {
        case result: PaymentAccountLoaded =>
          val paymentAccount = result.paymentAccount
          val legalUser = paymentAccount.getLegalUser
          val externalUuid = "other"
          ? (
            CreateOrUpdatePaymentAccount(
              paymentAccount.withLegalUser(
                legalUser.withLegalRepresentative(legalUser.legalRepresentative.withExternalUuid(externalUuid))
              )
            )
          ) await {
            case PaymentAccountCreated =>
              ? (LoadPaymentAccount(externalUuid)) await {
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
      ?(DeleteBankAccount(sellerUuid)) await {
        case BankAccountDeleted =>
          ? (LoadBankAccount(sellerUuid)) await {
            case BankAccountNotFound =>
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }

    "disable card" in {
      ?(DisableCard(customerUuid, cardId)) await {
        case CardDisabled =>
          ?(LoadCards(customerUuid)) await {
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
