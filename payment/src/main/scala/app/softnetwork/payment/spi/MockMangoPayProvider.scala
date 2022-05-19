package app.softnetwork.payment.spi

import java.text.SimpleDateFormat
import java.util
import java.util.{Calendar, Date, TimeZone}
import com.mangopay.core.{Address => MangoPayAddress, _}
import com.mangopay.core.enumerations.{TransactionStatus => MangoPayTransactionStatus, _}
import com.mangopay.entities.{BankAccount => MangoPayBankAccount, Card => _, KycDocument => _, Transaction => _, UboDeclaration => _, _}
import com.mangopay.entities.subentities._
import app.softnetwork.payment.model._
import app.softnetwork.payment.config.Settings
import Settings.MangoPayConfig._

import scala.util.{Failure, Success, Try}
import app.softnetwork.persistence._
import app.softnetwork.serialization._

import scala.language.implicitConversions


trait MockMangoPayProvider extends MangoPayProvider {

  import scala.collection.JavaConverters._

  import MockMangoPayProvider._

  private val OK = "000000"

  private val SUCCEEDED = "SUCCEEDED"

  private val CREATED = "CREATED"

  /**
    *
    * @param maybeNaturalUser - natural user to create
    * @return provider user id
    */
  override def createOrUpdateNaturalUser(maybeNaturalUser: Option[PaymentUser]): Option[String] =
    maybeNaturalUser match {
      case Some(naturalUser) =>
        import naturalUser._
        val sdf = new SimpleDateFormat("dd/MM/yyyy")
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
        Try(sdf.parse(birthday)) match {
          case Success(s) =>
            val user = new UserNatural
            user.setFirstName(firstName)
            user.setLastName(lastName)
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.setTime(s)
            user.setBirthday(c.getTimeInMillis / 1000)
            user.setEmail(email)
            user.setTag(externalUuid)
            user.setNationality(CountryIso.valueOf(nationality))
            user.setCountryOfResidence(CountryIso.valueOf(countryOfResidence))
            if (userId.getOrElse("").trim.isEmpty) {
              Users.values.find(_.getTag == externalUuid) match {
                case Some(u) => user.setId(u.getId)
                case _ => user.setId(generateUUID())
              }
            }
            else {
              user.setId(userId.get)
            }
            Users = Users.updated(user.getId, user)
            Some(user.getId)
          case Failure(f) =>
            mlog.error(f.getMessage, f)
            None
        }
      case _ => None
    }

  /**
    *
    * @param maybeLegalUser - legal user to create
    * @return provider user id
    */
  override def createOrUpdateLegalUser(maybeLegalUser: Option[LegalUser]): Option[String] = {
    maybeLegalUser match {
      case Some(legalUser) =>
        import legalUser._
        val sdf = new SimpleDateFormat("dd/MM/yyyy")
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
        Try(sdf.parse(legalRepresentative.birthday)) match {
          case Success(s) =>
            val user = new UserLegal
            user.setId(legalRepresentative.userId)
            val headquarters = new MangoPayAddress
            headquarters.setAddressLine1(headQuartersAddress.addressLine)
            headquarters.setCity(headQuartersAddress.city)
            headquarters.setCountry(CountryIso.valueOf(headQuartersAddress.country))
            headquarters.setPostalCode(headQuartersAddress.postalCode)
            user.setHeadquartersAddress(headquarters)
            user.setLegalPersonType(legalUserType)
            user.setName(legalName)
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.setTime(s)
            val address = new MangoPayAddress
            address.setAddressLine1(legalRepresentativeAddress.addressLine)
            address.setCity(legalRepresentativeAddress.city)
            address.setCountry(CountryIso.valueOf(legalRepresentativeAddress.country))
            address.setPostalCode(legalRepresentativeAddress.postalCode)
            user.setLegalRepresentativeAddress(address)
            user.setLegalRepresentativeBirthday(c.getTimeInMillis / 1000)
            user.setLegalRepresentativeCountryOfResidence(CountryIso.valueOf(legalRepresentative.countryOfResidence))
            user.setLegalRepresentativeFirstName(legalRepresentative.firstName)
            user.setLegalRepresentativeLastName(legalRepresentative.lastName)
            user.setLegalRepresentativeNationality(CountryIso.valueOf(legalRepresentative.nationality))
            user.setEmail(legalRepresentative.email)
            user.setCompanyNumber(siret)
            user.setTag(legalRepresentative.externalUuid)
            if (legalRepresentative.userId.trim.isEmpty) {
              LegalUsers.values.find(_.getTag == legalRepresentative.externalUuid) match {
                case Some(u) =>
                  user.setId(u.getId)
                  LegalUsers = LegalUsers.updated(user.getId, user)
                  Some(user.getId)
                case _ =>
                  user.setId(generateUUID())
                  LegalUsers = LegalUsers.updated(user.getId, user)
                  Some(user.getId)
              }
            }
            else {
              user.setId(legalRepresentative.userId.get)
              LegalUsers = LegalUsers.updated(user.getId, user)
              Some(user.getId)
            }
          case Failure(f) =>
            mlog.error(f.getMessage, f)
            None
        }
      case _ => None
    }
  }

  /**
    *
    * @param maybePayOutTransaction - pay out transaction
    * @param idempotency            - whether to use an idempotency key for this request or not
    * @return pay out transaction result
    */
  override def payOut(maybePayOutTransaction: Option[PayOutTransaction], idempotency: Option[Boolean] = None): Option[Transaction] =
    maybePayOutTransaction match {
      case Some(payOutTransaction) =>
        import payOutTransaction._
        val payOut = new PayOut
        payOut.setTag(orderUuid)
        payOut.setAuthorId(authorId)
        payOut.setCreditedUserId(creditedUserId)
        payOut.setDebitedFunds(new Money)
        payOut.getDebitedFunds.setAmount(debitedAmount)
        payOut.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        payOut.setFees(new Money)
        payOut.getFees.setAmount(feesAmount)
        payOut.getFees.setCurrency(CurrencyIso.EUR)
        payOut.setDebitedWalletId(debitedWalletId)
        val meanOfPaymentDetails = new PayOutPaymentDetailsBankWire
        meanOfPaymentDetails.setBankAccountId(bankAccountId)
        payOut.setMeanOfPaymentDetails(meanOfPaymentDetails)
        payOut.setId(orderUuid.substring(0, orderUuid.length - 1) + "o")
        mlog.info(s"debitedAmount -> $debitedAmount, fees -> $feesAmount")
        assert(debitedAmount > feesAmount)
        payOut.setStatus(MangoPayTransactionStatus.SUCCEEDED)
        payOut.setResultCode(OK)
        payOut.setResultMessage(SUCCEEDED)
        PayOuts = PayOuts.updated(payOut.getId, payOut)
        Some(
          Transaction().copy(
            id = payOut.getId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PAYOUT,
            status = payOut.getStatus,
            amount = debitedAmount,
            fees = feesAmount,
            resultCode = payOut.getResultCode,
            resultMessage = payOut.getResultMessage,
            authorId = Some(authorId),
            creditedUserId = Some(creditedUserId),
            debitedWalletId = Some(debitedWalletId)
          )
        )
      case _ => None
    }

  /**
    *
    * @param maybeBankAccount - bank account to create
    * @return bank account id
    */
  override def createOrUpdateBankAccount(maybeBankAccount: Option[BankAccount]): Option[String] =
    maybeBankAccount match {
      case Some(mangoPayBankAccount) =>
        import mangoPayBankAccount._
        val bankAccount = new MangoPayBankAccount
        bankAccount.setActive(true)
        val details = new BankAccountDetailsIBAN
        details.setIban(iban)
        details.setBic(bic)
        bankAccount.setDetails(details)
        bankAccount.setOwnerName(ownerName)
        val address = new MangoPayAddress
        address.setAddressLine1(ownerAddress.addressLine)
        address.setCity(ownerAddress.city)
        address.setCountry(CountryIso.valueOf(ownerAddress.country))
        address.setPostalCode(ownerAddress.postalCode)
        bankAccount.setOwnerAddress(address)
        bankAccount.setTag(tag)
        bankAccount.setType(BankAccountType.IBAN)
        bankAccount.setUserId(userId)
        BankAccounts.values.find(bankAccount => bankAccount.isActive && bankAccount.getId == bankAccountId.getOrElse("")) match {
          case Some(ba) if checkEquality(bankAccount, ba) =>
            bankAccount.setId(ba.getId)
            BankAccounts = BankAccounts.updated(bankAccount.getId, bankAccount)
            Some(bankAccount.getId)
          case _ =>
            bankAccount.setId(generateUUID())
            BankAccounts = BankAccounts.updated(bankAccount.getId, bankAccount)
            Some(bankAccount.getId)
        }
      case _ => None
    }

  /**
    *
    * @param maybeRefundTransaction - refund transaction
    * @param idempotency            - whether to use an idempotency key for this request or not
    * @return refund transaction result
    */
  override def refund(maybeRefundTransaction: Option[RefundTransaction], idempotency: Option[Boolean] = None): Option[Transaction] =
    maybeRefundTransaction match {
      case Some(refundTransaction) =>
        import refundTransaction._
        val refund = new Refund
        refund.setTag(orderUuid)
        refund.setInitialTransactionType(InitialTransactionType.PAYIN)
        refund.setInitialTransactionId(payInTransactionId)
        refund.setAuthorId(authorId)
        refund.setRefundReason(new RefundReason)
        refund.getRefundReason.setRefundReasonMessage(reasonMessage)
        if (initializedByClient) {
          refund.getRefundReason.setRefundReasonType(RefundReasonType.INITIALIZED_BY_CLIENT)
        }
        else {
          refund.getRefundReason.setRefundReasonType(RefundReasonType.OTHER)
        }
        refund.setDebitedFunds(new Money)
        refund.getDebitedFunds.setAmount(refundAmount)
        refund.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        refund.setFees(new Money)
        refund.getFees.setAmount(0) // fees are only set during transfer or payOut
        refund.getFees.setCurrency(CurrencyIso.EUR)

        refund.setId(orderUuid.substring(0, orderUuid.length - 1) + "r")
        refund.setStatus(MangoPayTransactionStatus.SUCCEEDED)
        refund.setResultCode(OK)
        refund.setResultMessage(SUCCEEDED)
        Refunds = Refunds.updated(refund.getId, refund)

        Some(
          Transaction().copy(
            id = refund.getId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REFUND,
            `type` = Transaction.TransactionType.PAYIN,
            status = refund.getStatus,
            amount = refundAmount,
            fees = 0,
            resultCode = refund.getResultCode,
            resultMessage = refund.getResultMessage,
            reasonMessage = Option(reasonMessage),
            authorId = Some(authorId)
          )
        )
      case _ => None
    }

  /**
    *
    * @param maybeTransferTransaction - transfer transaction
    * @return transfer transaction result
    */
  override def transfer(maybeTransferTransaction: Option[TransferTransaction]): Option[Transaction] = {
    maybeTransferTransaction match {
      case Some(transferTransaction) =>
        import transferTransaction._
        val transfer = new Transfer
        transfer.setAuthorId(authorId)
        transfer.setCreditedUserId(creditedUserId)
        transfer.setCreditedWalletId(creditedWalletId)
        transfer.setDebitedFunds(new Money)
        transfer.getDebitedFunds.setAmount(debitedAmount)
        transfer.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        transfer.setFees(new Money)
        transfer.getFees.setAmount(feesAmount)
        transfer.getFees.setCurrency(CurrencyIso.EUR)
        transfer.setDebitedWalletId(debitedWalletId)
        transfer.setId(generateUUID())

        transfer.setStatus(MangoPayTransactionStatus.SUCCEEDED)
        transfer.setResultCode(OK)
        transfer.setResultMessage(SUCCEEDED)
        Transfers = Transfers.updated(transfer.getId, transfer)
        Some(
          Transaction().copy(
            id = transfer.getId,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PAYOUT,
            status = transfer.getStatus,
            amount = debitedAmount,
            fees = feesAmount,
            resultCode = transfer.getResultCode,
            resultMessage = transfer.getResultMessage,
            authorId = Some(authorId),
            creditedUserId = Some(creditedUserId),
            creditedWalletId = Some(creditedWalletId),
            debitedWalletId = Some(debitedWalletId)
          )
        )
      case _ => None
    }
  }

  /**
    *
    * @param cardId - card id
    * @return card
    */
  override def loadCard(cardId: String): Option[Card] =
    Cards.get(cardId) match {
      case None =>
        CardRegistrations.values.find(_.getCardId == cardId) match {
          case Some(_) =>
            Cards = Cards.updated(cardId, Card.defaultInstance
              .withId(cardId)
              .withAlias("##################")
              .withExpirationDate(new SimpleDateFormat("MMyy").format(now()))
              .withActive(true)
            )
            Cards.get(cardId)
          case _ => None
        }
      case some => some
    }

  /**
    *
    * @param cardId - the id of the card to disable
    * @return the card disabled or none
    */
  override def disableCard(cardId: String): Option[Card] = {
    Cards.get(cardId) match {
      case Some(card) =>
        Cards = Cards.updated(card.id, card.withActive(false))
        Cards.get(card.id)
      case _ => None
    }
  }

  /**
    *
    * @param orderUuid     - order unique id
    * @param transactionId - transaction id
    * @return pay in transaction
    */
  override def loadPayIn(orderUuid: String, transactionId: String): Option[Transaction] =
    PayIns.get(transactionId) match {
      case Some(result) =>
        val `type` =
          if (result.getPaymentType == PayInPaymentType.DIRECT_DEBIT) {
            Transaction.TransactionType.DIRECT_DEBIT
          }
          else if (result.getPaymentType == PayInPaymentType.PREAUTHORIZED) {
            Transaction.TransactionType.PRE_AUTHORIZATION
          }
          else {
            Transaction.TransactionType.PAYIN
          }
        val cardId =
          if (result.getPaymentType == PayInPaymentType.CARD) {
            Option(result.getPaymentDetails.asInstanceOf[PayInPaymentDetailsCard].getCardId)
          }
          else {
            None
          }
        val redirectUrl =
          if (result.getExecutionType == PayInExecutionType.DIRECT) {
            Option( // for 3D Secure
              result.getExecutionDetails.asInstanceOf[PayInExecutionDetailsDirect].getSecureModeRedirectUrl
            )
          }
          else {
            None
          }
        val mandateId =
          if (result.getPaymentType == PayInPaymentType.DIRECT_DEBIT) {
            Option(result.getPaymentDetails.asInstanceOf[PayInPaymentDetailsDirectDebit].getMandateId)
          }
          else {
            None
          }
        val preAuthorizationId =
          if (result.getPaymentType == PayInPaymentType.PREAUTHORIZED) {
            Option(result.getExecutionDetails.asInstanceOf[PayInPaymentDetailsPreAuthorized].getPreauthorizationId)
          }
          else {
            None
          }
        Some(
          Transaction().copy(
            id = transactionId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = `type`,
            status = result.getStatus,
            amount = result.getDebitedFunds.getAmount,
            cardId = cardId,
            fees = result.getFees.getAmount,
            resultCode = result.getResultCode,
            resultMessage = result.getResultMessage,
            redirectUrl = redirectUrl,
            authorId = result.getAuthorId,
            creditedUserId = Option(result.getCreditedUserId),
            creditedWalletId = Option(result.getCreditedWalletId),
            mandateId = mandateId,
            preAuthorizationId = preAuthorizationId
          )
        )
      case _ => None
    }

  /**
    *
    * @param orderUuid     - order unique id
    * @param transactionId - transaction id
    * @return Refund transaction
    */
  override def loadRefund(orderUuid: String, transactionId: String): Option[Transaction] = None

  /**
    *
    * @param orderUuid     - order unique id
    * @param transactionId - transaction id
    * @return pay out transaction
    */
  override def loadPayOut(orderUuid: String, transactionId: String): Option[Transaction] = None

  /**
    *
    * @param transactionId - transaction id
    * @return transfer transaction
    */
  override def loadTransfer(transactionId: String): Option[Transaction] = None

  /**
    *
    * @param cardPreRegistrationId - card registration id
    * @param maybeRegistrationData - card registration data
    * @return card id
    */
  override def createCard(cardPreRegistrationId: String, maybeRegistrationData: Option[String]): Option[String] =
    maybeRegistrationData match {
      case Some(_) =>
        CardRegistrations.get(cardPreRegistrationId) match {
          case Some(cr) /* FIXME if cr.RegistrationData == registrationData*/ =>
            cr.setCardId(generateUUID())
            CardRegistrations = CardRegistrations.updated(cardPreRegistrationId, cr)
            Some(cr.getCardId)
          case _ => None
        }
      case _ => None
    }

  /**
    *
    * @param maybeUserId   - owner of the wallet
    * @param uuid          - external id
    * @param maybeWalletId - wallet id to update
    * @return wallet id
    */
  override def createOrUpdateWallet(maybeUserId: Option[String], uuid: String, maybeWalletId: Option[String]): Option[String] =
    maybeUserId match {
      case Some(userId) =>
        val wallet = new Wallet
        wallet.setCurrency(CurrencyIso.EUR)
        wallet.setOwners(new util.ArrayList(List(userId).asJava))
        wallet.setDescription(s"wallet for $uuid")
        wallet.setTag(uuid)
        maybeWalletId match {
          case Some(walletId) =>
            wallet.setId(walletId)
            Wallets = Wallets.updated(wallet.getId, wallet)
            Some(wallet.getId)
          case _ =>
            Wallets.values.find(_.getTag == uuid) match {
              case Some(w) =>
                wallet.setId(w.getId)
                Wallets = Wallets.updated(wallet.getId, wallet)
                Some(wallet.getId)
              case _ =>
                wallet.setId(generateUUID())
                Wallets = Wallets.updated(wallet.getId, wallet)
                Some(wallet.getId)
            }
        }
      case _ => None
    }

  /**
    *
    * @param userId - provider user id
    * @return the first active bank account
    */
  override def getActiveBankAccount(userId: String): Option[String] =
    BankAccounts.values.filter(bankAccount => bankAccount.getUserId == userId && bankAccount.isActive) match {
      case bas if bas.nonEmpty => Some(bas.toList.sortWith(_.getCreationDate > _.getCreationDate).head.getId)
      case _ => None
    }

  /**
    *
    * @param userId        - provider user id
    * @param bankAccountId - bank account id
    * @return whether this bank account exists and is active
    */
  override def checkBankAccount(userId: String, bankAccountId: String): Boolean =
    BankAccounts.values.find(bankAccount => bankAccount.getUserId == userId && bankAccount.getId == bankAccountId) match {
      case Some(ba) => ba.isActive
      case _ => false
    }

  /**
    *
    * @param maybePreAuthorizationTransaction - pre authorization transaction
    * @param idempotency                      - whether to use an idempotency key for this request or not
    * @return re authorization transaction result
    */
  override def preAuthorizeCard(maybePreAuthorizationTransaction: Option[PreAuthorizationTransaction], idempotency: Option[Boolean]): Option[Transaction] = {
    maybePreAuthorizationTransaction match {
      case Some(preAuthorizationTransaction) =>
        import preAuthorizationTransaction._
        val cardPreAuthorization = new CardPreAuthorization()
        cardPreAuthorization.setTag(orderUuid)
        cardPreAuthorization.setAuthorId(authorId)
        cardPreAuthorization.setCardId(cardId)
        cardPreAuthorization.setDebitedFunds(new Money)
        cardPreAuthorization.getDebitedFunds.setAmount(debitedAmount)
        cardPreAuthorization.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        cardPreAuthorization.setExecutionType(PreAuthorizationExecutionType.DIRECT)
        cardPreAuthorization.setSecureMode(SecureMode.DEFAULT)
        cardPreAuthorization.setSecureModeReturnUrl(
          s"$secureModeReturnUrl/$orderUuid?registerCard=${registerCard.getOrElse(false)}"
        )

        cardPreAuthorization.setId(generateUUID())
        cardPreAuthorization.setStatus(PreAuthorizationStatus.CREATED)
        cardPreAuthorization.setResultCode(OK)
        cardPreAuthorization.setResultMessage(CREATED)
        cardPreAuthorization.setSecureModeRedirectUrl(
          s"${cardPreAuthorization.getSecureModeReturnUrl}&preAuthorizationId=${cardPreAuthorization.getId}"
        )
        CardPreAuthorizations = CardPreAuthorizations.updated(cardPreAuthorization.getId, cardPreAuthorization)

        Some(
          Transaction().copy(
            id = cardPreAuthorization.getId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PRE_AUTHORIZATION,
            status = cardPreAuthorization.getStatus,
            amount = debitedAmount,
            cardId = cardId,
            fees = 0,
            resultCode = cardPreAuthorization.getResultCode,
            resultMessage = cardPreAuthorization.getResultMessage,
            redirectUrl = if(debitedAmount > 5000) Option(cardPreAuthorization.getSecureModeRedirectUrl) else None,
            authorId = cardPreAuthorization.getAuthorId,
            paymentType = Transaction.PaymentType.CARD
          )
        )
      case _ => None
    }
  }

  /**
    *
    * @param orderUuid                      - order unique id
    * @param cardPreAuthorizedTransactionId - card pre authorized transaction id
    * @return card pre authorized transaction
    */
  override def loadCardPreAuthorized(orderUuid: String, cardPreAuthorizedTransactionId: String): Option[Transaction] = {
    CardPreAuthorizations.get(cardPreAuthorizedTransactionId) match {
      case Some(result) =>
        Some(
          Transaction().copy(
            id = result.getId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PRE_AUTHORIZATION,
            status = result.getStatus,
            amount = result.getDebitedFunds.getAmount,
            cardId = result.getCardId,
            fees = 0,
            resultCode = Option(result.getResultCode).getOrElse(""),
            resultMessage = Option(result.getResultMessage).getOrElse(""),
            redirectUrl = None /*Option( // for 3D Secure
              result.getSecureModeRedirectUrl
            )*/,
            authorId = result.getAuthorId
          )
        )
      case _ => None
    }
  }

  /**
    *
    * @param maybePayInWithCardPreAuthorizedTransaction - card pre authorized pay in transaction
    * @param idempotency                                - whether to use an idempotency key for this request or not
    * @return pay in with card pre authorized transaction result
    */
  override def payInWithCardPreAuthorized(maybePayInWithCardPreAuthorizedTransaction: Option[PayInWithCardPreAuthorizedTransaction], idempotency: Option[Boolean]): Option[Transaction] = {
    maybePayInWithCardPreAuthorizedTransaction match {
      case Some(payInWithCardPreAuthorizedTransaction) =>
        import payInWithCardPreAuthorizedTransaction._
        val payIn = new PayIn()
        payIn.setTag(orderUuid)
        payIn.setCreditedWalletId(creditedWalletId)
        payIn.setAuthorId(authorId)
        payIn.setDebitedFunds(new Money)
        payIn.getDebitedFunds.setAmount(debitedAmount)
        payIn.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        payIn.setExecutionType(PayInExecutionType.DIRECT)
        payIn.setFees(new Money)
        payIn.getFees.setAmount(0) // fees are only set during transfer or payOut
        payIn.getFees.setCurrency(CurrencyIso.EUR)
        payIn.setPaymentType(PayInPaymentType.PREAUTHORIZED)
        val paymentDetails = new PayInPaymentDetailsPreAuthorized
        paymentDetails.setPreauthorizationId(cardPreAuthorizedTransactionId)
        payIn.setPaymentDetails(paymentDetails)

        payIn.setId(generateUUID())
        payIn.setStatus(MangoPayTransactionStatus.SUCCEEDED)
        payIn.setResultCode(OK)
        payIn.setResultMessage(SUCCEEDED)
        PayIns = PayIns.updated(payIn.getId, payIn)
        Some(
          Transaction().copy(
            id = payIn.getId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PAYIN,
            status = payIn.getStatus,
            amount = debitedAmount,
            cardId = "",
            fees = 0,
            resultCode = Option(payIn.getResultCode).getOrElse(""),
            resultMessage = Option(payIn.getResultMessage).getOrElse(""),
            redirectUrl = "",
            authorId = payIn.getAuthorId,
            creditedWalletId = Option(payIn.getCreditedWalletId),
            preAuthorizationId = Some(cardPreAuthorizedTransactionId)
          )
        )
      case None => None
    }
  }

  /**
    *
    * @param orderUuid                      - order unique id
    * @param cardPreAuthorizedTransactionId - card pre authorized transaction id
    * @return pre authorization cancellation transaction
    */
  override def cancelPreAuthorization(orderUuid: String, cardPreAuthorizedTransactionId: String): Boolean = {
    CardPreAuthorizations.get(cardPreAuthorizedTransactionId) match {
      case Some(result) =>
        result.setPaymentStatus(PaymentStatus.CANCELED)
        CardPreAuthorizations = CardPreAuthorizations.updated(result.getId, result)
        true
      case _ => false
    }
  }

  /**
    *
    * @param maybePayInTransaction - pay in transaction
    * @param idempotency           - whether to use an idempotency key for this request or not
    * @return pay in transaction result
    */
  override def payIn(maybePayInTransaction: Option[PayInTransaction], idempotency: Option[Boolean] = None): Option[Transaction] =
    maybePayInTransaction match {
      case Some(payInTransaction) =>
        import payInTransaction._
        val payIn = new PayIn()
        payIn.setTag(orderUuid)
        payIn.setCreditedWalletId(creditedWalletId)
        payIn.setAuthorId(authorId)
        payIn.setDebitedFunds(new Money)
        payIn.getDebitedFunds.setAmount(debitedAmount)
        payIn.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        payIn.setFees(new Money)
        payIn.getFees.setAmount(0) // fees are only set during transfer or payOut
        payIn.getFees.setCurrency(CurrencyIso.EUR)
        payIn.setPaymentType(PayInPaymentType.CARD)
        val paymentDetails = new PayInPaymentDetailsCard
        paymentDetails.setCardId(cardId)
        paymentDetails.setCardType(CardType.CB_VISA_MASTERCARD)
        paymentDetails.setStatementDescriptor("SOFTNETWORK")
        payIn.setPaymentDetails(paymentDetails)
        payIn.setExecutionType(PayInExecutionType.DIRECT)
        val executionDetails = new PayInExecutionDetailsDirect
        executionDetails.setCardId(cardId)
        // Secured Mode is activated from €100.
        executionDetails.setSecureMode(SecureMode.DEFAULT)
        executionDetails.setSecureModeReturnUrl(s"$secureModeReturnUrl/$orderUuid")
        payIn.setExecutionDetails(executionDetails)

        payIn.setId(orderUuid.substring(0, orderUuid.length - 1) + "p")
        payIn.setStatus(MangoPayTransactionStatus.SUCCEEDED)
        payIn.setResultCode(OK)
        payIn.setResultMessage(SUCCEEDED)
        PayIns = PayIns.updated(payIn.getId, payIn)

        Some(
          Transaction().copy(
            id = payIn.getId,
            orderUuid = orderUuid,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PAYIN,
            status = payIn.getStatus,
            amount = debitedAmount,
            cardId = Option(cardId),
            fees = 0,
            resultCode = payIn.getResultCode,
            resultMessage = payIn.getResultMessage,
            redirectUrl = None,
            authorId = Some(authorId),
            creditedWalletId = Some(creditedWalletId)
          )
        )
      case _ => None
    }

  /**
    *
    * @param maybeUserId - owner of the card
    * @param uuid        - external id
    * @return card pre registration
    */
  override def preRegisterCard(maybeUserId: Option[String], uuid: String): Option[CardPreRegistration] =
    maybeUserId match {
      case Some(userId) =>
        val cardPreRegistration = new CardRegistration()
        cardPreRegistration.setCurrency(CurrencyIso.EUR)
        cardPreRegistration.setTag(uuid)
        cardPreRegistration.setUserId(userId)
        cardPreRegistration.setId(generateUUID())
        cardPreRegistration.setAccessKey("key")
        cardPreRegistration.setPreregistrationData("data")
        cardPreRegistration.setCardRegistrationUrl("url")
        CardRegistrations = CardRegistrations.updated(cardPreRegistration.getId, cardPreRegistration)
        Some(
          CardPreRegistration.defaultInstance
            .withId(cardPreRegistration.getId)
            .withAccessKey(cardPreRegistration.getAccessKey)
            .withPreregistrationData(cardPreRegistration.getPreregistrationData)
            .withRegistrationURL(cardPreRegistration.getCardRegistrationUrl)
        )
      case _ => None
    }

  /**
    *
    * @param userId       - Provider user id
    * @param uuid         - System entity id
    * @param pages        - document pages
    * @param documentType - document type
    * @return Provider document id
    */
  override def addDocument(userId: String, uuid: String, pages: Seq[Array[Byte]], documentType: KycDocument.KycDocumentType): Option[String] = {
    val documentId = generateUUID()
    Documents = Documents.updated(
      documentId,
      KycDocumentValidationReport.defaultInstance
        .withUserId(userId)
        .withDocumentId(documentId)
        .withStatus(KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
    )
    Some(documentId)
  }

  /**
    *
    * @param userId     - Provider user id
    * @param documentId - Provider document id
    * @return document validation report
    */
  override def loadDocumentStatus(userId: String, documentId: String): KycDocumentValidationReport =
    Documents.getOrElse(documentId,
      KycDocumentValidationReport.defaultInstance
        .withUserId(userId)
        .withDocumentId(documentId)
        .withStatus(KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED)
    )

  /**
    *
    * @param storeUuid      - Store unique id
    * @param userId         - Provider user id
    * @param bankAccountId  - Bank account id
    * @param idempotencyKey - whether to use an idempotency key for this request or not
    * @return mandate result
    */
  override def mandate(storeUuid: String, userId: String, bankAccountId: String, idempotencyKey: Option[String] = None): Option[MandateResult] = {
    val mandate = new Mandate()
    mandate.setId(generateUUID())
    mandate.setBankAccountId(bankAccountId)
    mandate.setCulture(CultureCode.FR)
    mandate.setExecutionType(MandateExecutionType.WEB)
    mandate.setMandateType(MandateType.DIRECT_DEBIT)
    mandate.setReturnUrl(s"$directDebitReturnUrl?StoreUuid=$storeUuid&idempotencyKey=${idempotencyKey.getOrElse("")}")
    mandate.setScheme(MandateScheme.SEPA)
    mandate.setStatus(MandateStatus.SUBMITTED)
    mandate.setUserId(userId)
    Mandates = Mandates.updated(mandate.getId, mandate)
    Some(
      MandateResult.defaultInstance.withId(mandate.getId).withStatus(mandate.getStatus)
    )
  }

  /**
    *
    * @param maybeMandateId - optional mandate id
    * @param userId         - Provider user id
    * @param bankAccountId  - bank account id
    * @return mandate associated to this bank account
    */
  override def loadMandate(maybeMandateId: Option[String], userId: String, bankAccountId: String): Option[MandateResult] = {
    maybeMandateId match {
      case Some(mandateId) =>
        Mandates.get(mandateId) match {
          case Some(s) if s.getBankAccountId == bankAccountId && s.getUserId == userId =>
            Some(
              MandateResult.defaultInstance.withId(s.getId).withStatus(s.getStatus)
            )
          case _ => None
        }
      case _ =>
        Mandates.values.
          filter(m => m.getBankAccountId == bankAccountId && m.getUserId == userId).
          map(m => MandateResult.defaultInstance.withId(m.getId).withStatus(m.getStatus)).
          headOption
    }
  }

  /**
    *
    * @param mandateId - Provider mandate id
    * @return mandate result
    */
  override def cancelMandate(mandateId: String): Option[MandateResult] = {
    Mandates.get(mandateId) match {
      case Some(mandate) =>
        Mandates = Mandates.updated(mandateId, null)
        Some(
          MandateResult.defaultInstance.withId(mandate.getId).withStatus(mandate.getStatus)
        )
      case _ => None
    }
  }

  /**
    *
    * @param maybeDirectDebitTransaction - direct debit transaction
    * @param idempotency                 - whether to use an idempotency key for this request or not
    * @return direct debit transaction result
    */
  override def directDebit(maybeDirectDebitTransaction: Option[DirectDebitTransaction], idempotency: Option[Boolean] = None): Option[Transaction] = {
    maybeDirectDebitTransaction match {
      case Some(directDebitTransaction) =>
        import directDebitTransaction._
        val payIn = new PayIn()
        payIn.setAuthorId(authorId)
        payIn.setCreditedWalletId(creditedWalletId)
        payIn.setDebitedFunds(new Money)
        payIn.getDebitedFunds.setAmount(debitedAmount)
        payIn.getDebitedFunds.setCurrency(CurrencyIso.EUR)
        payIn.setFees(new Money)
        payIn.getFees.setAmount(feesAmount)
        payIn.getFees.setCurrency(CurrencyIso.EUR)
        payIn.setPaymentType(PayInPaymentType.DIRECT_DEBIT)
        val paymentDetails = new PayInPaymentDetailsDirectDebit
        paymentDetails.setCulture(CultureCode.FR)
        paymentDetails.setMandateId(mandateId)
        paymentDetails.setStatementDescriptor(statementDescriptor)
        payIn.setPaymentDetails(paymentDetails)
        payIn.setExecutionType(PayInExecutionType.DIRECT)
        val executionDetails = new PayInExecutionDetailsDirect
        executionDetails.setCulture(CultureCode.FR)
        // Secured Mode is activated from €100.
        executionDetails.setSecureMode(SecureMode.DEFAULT)
        payIn.setExecutionDetails(executionDetails)

        payIn.setId(generateUUID())
        payIn.setStatus(MangoPayTransactionStatus.SUCCEEDED)
        payIn.setResultCode(OK)
        payIn.setResultMessage(SUCCEEDED)
        PayIns = PayIns.updated(payIn.getId, payIn)
        ClientFees += feesAmount.toDouble / 100
        Some(
          Transaction().copy(
            id = payIn.getId,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.DIRECT_DEBIT,
            status = payIn.getStatus,
            amount = debitedAmount,
            fees = feesAmount,
            resultCode = payIn.getResultCode,
            resultMessage = payIn.getResultMessage,
            redirectUrl = None,
            authorId = Some(authorId),
            creditedUserId = Some(creditedUserId),
            creditedWalletId = Some(creditedWalletId),
            mandateId = Some(mandateId)
          )
        )
      case _ => None
    }
  }

  /**
    *
    * @param walletId        - Provider wallet id
    * @param transactionId   - Provider transaction id
    * @param transactionDate - Provider transaction date
    * @return transaction if it exists
    */
  override def directDebitTransaction(walletId: String, transactionId: String, transactionDate: Date): Option[Transaction] = {
    PayIns.get(transactionId) match {
      case Some(payIn) =>
        Some(
          Transaction().copy(
            id = payIn.getId,
            nature = Transaction.TransactionNature.REGULAR,
            `type` = Transaction.TransactionType.PAYIN,
            status = payIn.getStatus,
            amount = payIn.getDebitedFunds.getAmount,
            fees = payIn.getFees.getAmount,
            resultCode = payIn.getResultCode,
            resultMessage = payIn.getResultMessage,
            redirectUrl = None,
            authorId = payIn.getAuthorId,
            creditedUserId = Option(payIn.getCreditedUserId),
            creditedWalletId = Option(payIn.getCreditedWalletId)
          )
        )
      case _ => None
    }
  }

  /**
    *
    * @return client fees
    */
  override def clientFees(): Option[Double] = Some(ClientFees)

  /**
    *
    * @param userId - Provider user id
    * @return Ultimate Beneficial Owner Declaration
    */
  override def createDeclaration(userId: String): Option[UboDeclaration] = {
    val uboDeclaration = UboDeclaration.defaultInstance
      .withUboDeclarationId(generateUUID())
      .withStatus(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_CREATED)
      .withCreatedDate(now())
    UboDeclarations = UboDeclarations.updated(uboDeclaration.uboDeclarationId, uboDeclaration)
    Some(uboDeclaration)
  }

  /**
    *
    * @param userId                  - Provider user id
    * @param uboDeclarationId        - Provider declaration id
    * @param ultimateBeneficialOwner - Ultimate Beneficial Owner
    * @return Ultimate Beneficial Owner created or updated
    */
  override def createOrUpdateUBO(userId: String, uboDeclarationId: String, ultimateBeneficialOwner: UboDeclaration.UltimateBeneficialOwner): Option[UboDeclaration.UltimateBeneficialOwner] = {
    UboDeclarations.get(uboDeclarationId) match {
      case Some(uboDeclaration) =>
        ultimateBeneficialOwner.id match {
          case Some(id) =>
            UboDeclarations = UboDeclarations.updated(
              uboDeclarationId,
              uboDeclaration
                .withUbos(uboDeclaration.ubos.filterNot(_.id.getOrElse("") == id) :+ ultimateBeneficialOwner)
            )
            Some(ultimateBeneficialOwner)
          case _ =>
            val updated = ultimateBeneficialOwner.withId(generateUUID())
            UboDeclarations = UboDeclarations.updated(
              uboDeclarationId,
              uboDeclaration.withUbos(uboDeclaration.ubos :+ updated)
            )
            Some(updated)
        }
      case _ => None
    }
  }

  /**
    *
    * @param userId           - Provider user id
    * @param uboDeclarationId - Provider declaration id
    * @return declaration with Ultimate Beneficial Owner(s)
    */
  override def getDeclaration(userId: String, uboDeclarationId: String): Option[UboDeclaration] =
    UboDeclarations.get(uboDeclarationId)

  /**
    *
    * @param userId           - Provider user id
    * @param uboDeclarationId - Provider declaration id
    * @return Ultimate Beneficial Owner declaration
    */
  override def validateDeclaration(userId: String, uboDeclarationId: String): Option[UboDeclaration] = {
    UboDeclarations.get(uboDeclarationId) match {
      case Some(uboDeclaration) =>
        val updated = uboDeclaration.withStatus(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATION_ASKED)
        UboDeclarations = UboDeclarations.updated(
          uboDeclarationId,
          updated
        )
        Some(updated)
      case _ => None
    }
   }
}

object MockMangoPayProvider {
  var Users: Map[String, UserNatural] = Map.empty

  var LegalUsers: Map[String, UserLegal] = Map.empty

  var Wallets: Map[String, Wallet] = Map.empty

  var BankAccounts: Map[String, MangoPayBankAccount] = Map.empty

  var CardRegistrations: Map[String, CardRegistration] = Map.empty

  var Cards: Map[String, Card] = Map.empty

  var PayIns: Map[String, PayIn] = Map.empty

  var CardPreAuthorizations: Map[String, CardPreAuthorization] = Map.empty

  var PayOuts: Map[String, PayOut] = Map.empty

  var Refunds: Map[String, Refund] = Map.empty

  var Transfers: Map[String, Transfer] = Map.empty

  var Mandates: Map[String, Mandate] = Map.empty

  var Documents: Map[String, KycDocumentValidationReport] = Map.empty

  var UboDeclarations: Map[String, UboDeclaration] = Map.empty

  var ClientFees: Double = 0D

}
