package app.softnetwork.payment.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import app.softnetwork.api.server.config.Settings.RootPath
import app.softnetwork.payment.config.Settings.MangoPayConfig._
import app.softnetwork.payment.config.Settings.PaymentPath
import app.softnetwork.payment.handlers.MockPaymentDao
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner
import app.softnetwork.payment.model.UboDeclaration.UltimateBeneficialOwner.BirthPlace
import app.softnetwork.payment.model._
import app.softnetwork.payment.scalatest.PaymentRouteTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class PaymentServiceSpec extends AnyWordSpecLike with PaymentRouteTestKit{

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

  import app.softnetwork.serialization._

  "Payment service" must {
    "pre register card" in {
      createSession(customerUuid)
      withCookies(
        Post(s"/$RootPath/$PaymentPath/card", PreRegisterCard(
          orderUuid,
          naturalUser.withExternalUuid(customerUuid)
        ))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        cardPreRegistration = responseAs[CardPreRegistration]
      }
    }

    "pre authorize card" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/preAuthorize", Payment(
          orderUuid,
          5100,
          Some(cardPreRegistration)
        ))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val redirection = responseAs[PaymentRedirection]
        val params = redirection.redirectUrl.split("\\?").last.split("&|=")
          .grouped(2)
          .map(a => (a(0), a(1)))
          .toMap
        preAuthorizationId = params.getOrElse("preAuthorizationId", "")
      }
    }

    "card pre authorization with 3ds" in {
      Get(s"/$RootPath/$PaymentPath/$secureModeRoute/preAuthorize/$orderUuid?preAuthorizationId=$preAuthorizationId&registerCard=true"
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val paymentAccount = loadPaymentAccount()
        logger.info(serialization.write(paymentAccount))
        assert(paymentAccount.cards.nonEmpty)
      }
    }

    "load cards" in {
      val card = loadCards().head
      assert(card.firstName == firstName)
      assert(card.lastName == lastName)
      assert(card.birthday == birthday)
      assert(card.getActive)
      assert(!card.expired)
      cardId = card.id
    }

    "not create bank account with wrong iban" in {
      createSession(sellerUuid)
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank", BankAccountCommand(
          BankAccount(None, ownerName, ownerAddress, "", bic),
          naturalUser.withExternalUuid(sellerUuid),
          None
        ))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        assert(responseAs[PaymentError].message == WrongIban.message)
      }
    }

    "not create bank account with wrong bic" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank", BankAccountCommand(
          BankAccount(None, ownerName, ownerAddress, iban, ""),
          naturalUser.withExternalUuid(sellerUuid),
          None
        ))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        assert(responseAs[PaymentError].message == WrongBic.message)
      }
    }

    "create bank account with natural user" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank", BankAccountCommand(
          BankAccount(None, ownerName, ownerAddress, iban, bic),
          naturalUser.withExternalUuid(sellerUuid),
          None
        ))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val bankAccount = loadBankAccount()
        sellerBankAccountId = bankAccount.bankAccountId
      }
    }

    "update bank account with natural user" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank", BankAccountCommand(
          BankAccount(Some(sellerBankAccountId), ownerName, ownerAddress, iban, bic),
          naturalUser.withLastName("anotherLastName").withExternalUuid(sellerUuid),
          None
        ))
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val bankAccount = loadBankAccount()
        val previousBankAccountId = sellerBankAccountId
        sellerBankAccountId = bankAccount.bankAccountId
        assert(sellerBankAccountId != previousBankAccountId)
      }
    }

    "not update bank account with wrong siret" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank",
          BankAccountCommand(
            BankAccount(
              Some(sellerBankAccountId),
              ownerName,
              ownerAddress,
              iban,
              bic
            ),
            legalUser.withSiret(""),
            None
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        assert(responseAs[PaymentError].message == WrongSiret.message)
      }
    }

    "not update bank account with empty legal name" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank",
          BankAccountCommand(
            BankAccount(
              Some(sellerBankAccountId),
              ownerName,
              ownerAddress,
              iban,
              bic
            ),
            legalUser.withLegalName(""),
            None
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        assert(responseAs[PaymentError].message == LegalNameRequired.message)
      }
    }

    "not update bank account without accepted terms of PSP" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank",
          BankAccountCommand(
            BankAccount(
              Some(sellerBankAccountId),
              ownerName,
              ownerAddress,
              iban,
              bic
            ),
            legalUser,
            None
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        assert(responseAs[PaymentError].message == AcceptedTermsOfPSPRequired.message)
      }
    }

    "update bank account with sole trader legal user" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank",
          BankAccountCommand(
            BankAccount(
              Some(sellerBankAccountId),
              ownerName,
              ownerAddress,
              iban,
              bic
            ),
            legalUser,
            Some(true)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val bankAccount = loadBankAccount()
        val previousBankAccountId = sellerBankAccountId
        sellerBankAccountId = bankAccount.bankAccountId
        assert(sellerBankAccountId != previousBankAccountId)
      }
    }

    "update bank account with business legal user" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/bank",
          BankAccountCommand(
            BankAccount(
              Some(sellerBankAccountId),
              ownerName,
              ownerAddress,
              iban,
              bic
            ),
            legalUser.withLegalUserType(LegalUser.LegalUserType.BUSINESS),
            Some(true)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val bankAccount = loadBankAccount()
        val previousBankAccountId = sellerBankAccountId
        sellerBankAccountId = bankAccount.bankAccountId
        assert(sellerBankAccountId != previousBankAccountId)
      }
    }

    "add document(s)" in {
      addKycDocuments()
    }

    "update document(s) status" in {
      validateKycDocuments()
    }

    "create or update ultimate beneficial owner" in {
      withCookies(
        Post(s"/$RootPath/$PaymentPath/declaration", ubo)
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val declaration = loadDeclaration()
        assert(declaration.ubos.size == 1)
        uboDeclarationId = declaration.uboDeclarationId
      }
    }

    "ask for declaration validation" in {
      withCookies(
        Put(s"/$RootPath/$PaymentPath/declaration")
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val declaration = loadDeclaration()
        assert(declaration.status == UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATION_ASKED)
      }
    }

    "update declaration status" in {
      Get(s"/$RootPath/$PaymentPath/$hooksRoute?EventType=UBO_DECLARATION_VALIDATED&RessourceId=$uboDeclarationId"
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val declaration = loadDeclaration()
        assert(declaration.status == UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED)
      }
    }

    "pay in / out with pre authorized card" in {
      createSession(customerUuid)
      withCookies(
        Post(s"/$RootPath/$PaymentPath/preAuthorize",
          Payment(
            orderUuid,
            100,
            Some(cardPreRegistration)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        preAuthorizationId = responseAs[CardPreAuthorized].transactionId
        implicit val tSystem: ActorSystem[_] = typedSystem()
        MockPaymentDao ? PayInWithCardPreAuthorized(preAuthorizationId, sellerUuid) await {
          case _: PaidIn =>
            MockPaymentDao ? PayOut(orderUuid, sellerUuid, 100) await {
              case _: PaidOut =>
              case other => fail(other.toString)
            }
          case other => fail(other.toString)
        }
      }
    }

    "pay in / out with 3ds" in {
      createSession(customerUuid)
      withCookies(
        Post(s"/$RootPath/$PaymentPath?creditedAccount=$sellerUuid",
          Payment(
            orderUuid,
            5100,
            Some(cardPreRegistration)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val redirection = responseAs[PaymentRedirection]
        val params = redirection.redirectUrl.split("\\?").last.split("&|=")
          .grouped(2)
          .map(a => (a(0), a(1)))
          .toMap
        val transactionId = params.getOrElse("transactionId", "")
        Get(s"/$RootPath/$PaymentPath/$secureModeRoute/payIn/$orderUuid?transactionId=$transactionId&registerCard=true"
        ) ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          assert(responseAs[PaidIn].transactionId == transactionId)
          implicit val tSystem: ActorSystem[_] = typedSystem()
          MockPaymentDao ? PayOut(orderUuid, sellerUuid, 5100) await {
            case _: PaidOut =>
            case other => fail(other.toString)
          }
        }
      }
    }

    "delete bank account" in {
      createSession(sellerUuid)
      withCookies(
        Delete(s"/$RootPath/$PaymentPath/bank")
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        assert(loadPaymentAccount().bankAccount.isEmpty)
      }
    }

    "disable card" in {
      createSession(customerUuid)
      withCookies(
        Delete(s"/$RootPath/$PaymentPath/card?cardId=$cardId")
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val cards = loadCards()
        val card = cards.find(_.id == cardId)
        assert(card.map(_.firstName).getOrElse("") == firstName)
        assert(card.map(_.lastName).getOrElse("") == lastName)
        assert(card.map(_.birthday).getOrElse("") == birthday)
        assert(card.exists(!_.getActive))
      }
    }

  }

}
