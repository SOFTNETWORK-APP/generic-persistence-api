package app.softnetwork.payment.message

import app.softnetwork.payment.model._
import app.softnetwork.persistence._
import app.softnetwork.persistence.message._

object PaymentMessages {
  trait PaymentCommand extends Command

  trait PaymentCommandWithKey extends PaymentCommand{
    def key: String
  }

  /** Payment Commands */

  /**
    *
    * @param orderUuid - order uuid
    * @param user - payment user
    */
  case class PreRegisterCard(orderUuid: String, user: PaymentUser) extends PaymentCommandWithKey {
    val key: String = user.userId.getOrElse(generateUUID())
  }

  case class CardPreAuthorization(orderUuid: String,
                                  debitedAmount: Int = 100,
                                  cardPreRegistration: Option[CardPreRegistration] = None,                                  javaEnabled: Boolean = false,
                                  javascriptEnabled: Boolean = true,
                                  colorDepth: Option[Int] = None,
                                  screenWidth: Option[Int] = None,
                                  screenHeight: Option[Int] = None)

  /**
    * Flow [PreRegisterCard -> ] PreAuthorizeCard
    * @param orderUuid - order uuid
    * @param debitedAccount - account to debit
    * @param debitedAmount - amount to debit from the debited account
    * @param cardPreRegistration - optional card pre registration
    * @param javaEnabled - java enabled
    * @param ipAddress - ip address
    * @param browserInfo - browser info
    */
  private[payment] case class PreAuthorizeCard(orderUuid: String,
                              debitedAccount: String,
                              debitedAmount: Int = 100,
                              cardPreRegistration: Option[CardPreRegistration] = None,                                  javaEnabled: Boolean = false,
                              ipAddress: Option[String] = None,
                              browserInfo: Option[BrowserInfo] = None
                             ) extends PaymentCommandWithKey {
    val key: String = debitedAccount
  }

  /**
    * hook command
    *
    * @param orderUuid - order unique id
    * @param preAuthorizationId - pre authorization transaction id
    * @param registerCard - whether the card should be registered or not
    */
  private[payment] case class PreAuthorizeCardFor3DS(orderUuid: String,
                                                       preAuthorizationId: String,
                                                       registerCard: Boolean = true)
    extends PaymentCommandWithKey {
    lazy val key: String = preAuthorizationId
  }

  /**
    * Flow [PreRegisterCard -> ] PreAuthorizeCard -> PayInWithCardPreAuthorized
    * @param preAuthorizationId - pre authorization transaction id
    * @param creditedAccount - account to credit
    */
  case class PayInWithCardPreAuthorized(preAuthorizationId: String, creditedAccount: String)
    extends PaymentCommandWithKey {
    lazy val key: String = preAuthorizationId
  }

  /**
    * Flow [PreRegisterCard ->] PayIn
    * @param orderUuid - order uuid
    * @param debitedAccount - account to debit
    * @param debitedAmount - amount to be debited from the debited account
    * @param creditedAccount - account to credit
    * @param cardPreRegistration - optional card pre registration
    * @param ipAddress - ip address
    * @param browserInfo - browser info
    */
  case class PayIn(orderUuid: String,
                   debitedAccount: String,
                   debitedAmount: Int,
                   creditedAccount: String,
                   cardPreRegistration: Option[CardPreRegistration] = None,
                   ipAddress: Option[String] = None,
                   browserInfo: Option[BrowserInfo] = None
                  ) extends PaymentCommandWithKey {
    val key: String = debitedAccount
  }

  /**
    * hook command
    *
    * @param orderUuid - order unique id
    * @param transactionId - payin transaction id
    * @param registerCard -  the card should be registered or not
    */
  private[payment] case class PayInFor3DS(orderUuid: String, transactionId: String, registerCard: Boolean)
    extends PaymentCommandWithKey {
    lazy val key: String = transactionId
  }

  case class PayOut(orderUuid: String,
                    creditedAccount: String,
                    creditedAmount: Int,
                    feesAmount: Int = 0
                   ) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  case class Refund(orderUuid: String, payInTransactionId: String, refundAmount: Int, reasonMessage: String, initializedByClient: Boolean)
    extends PaymentCommandWithKey {
    lazy val key: String = payInTransactionId
  }

  case class Transfer(orderUuid: String,
                      debitedAccount: String,
                      creditedAccount: String,
                      debitedAmount: Int,
                      feesAmount: Int = 0,
                      payOutRequired: Boolean = true) extends PaymentCommandWithKey {
    val key: String = debitedAccount
  }

  /** Commands related to the payment account */

  /**
    * private api command
    * @param account - payment account reference
    */
  private[payment] case class LoadPaymentAccount(account: String)
    extends PaymentCommandWithKey {
    lazy val key: String = account
  }

  case class LoadTransaction(transactionId: String)
    extends PaymentCommandWithKey {
    lazy val key: String = transactionId
  }

  /** Commands related to the bank account */

  case class BankAccountCommand(bankAccount: BankAccount,
                                                   naturalUser: Option[PaymentUser]= None,
                                                   legalUser: Option[LegalUser] = None,
                                                   acceptedTermsOfPSP: Option[Boolean] = None)

  case class CreateOrUpdateBankAccount(creditedAccount: String,
                                       bankAccount: BankAccount,
                                       user: Option[PaymentAccount.User]= None,
                                       acceptedTermsOfPSP: Option[Boolean] = None) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  case class LoadBankAccount(creditedAccount: String) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  case class DeleteBankAccount(creditedAccount: String) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  case class LoadCards(debitedAccount: String) extends PaymentCommandWithKey {
    val key: String = debitedAccount
  }

  case class DisableCard(debitedAccount: String, cardId: String) extends PaymentCommandWithKey {
    val key: String = debitedAccount
  }

  /** Commands related to the kyc documents */

  case class AddKycDocument(creditedAccount: String,
                            pages: Seq[Array[Byte]],
                            kycDocumentType: KycDocument.KycDocumentType) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  /**
    * hook command
    *
    * @param kycDocumentId - kyc document id
    * @param status - kyc document status
    */
  private[payment] case class UpdateKycDocumentStatus(kycDocumentId: String,
                                                        status: Option[KycDocument.KycDocumentStatus] = None)
    extends PaymentCommandWithKey{
    lazy val key: String = kycDocumentId
  }

  case class LoadKycDocumentStatus(creditedAccount: String,
                                   kycDocumentType: KycDocument.KycDocumentType) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  /** Commands related to the ubo declaration */

  case class CreateOrUpdateUbo(creditedAccount: String, ubo: UboDeclaration.UltimateBeneficialOwner)
    extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  case class ValidateUboDeclaration(creditedAccount: String) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  case class GetUboDeclaration(creditedAccount: String) extends PaymentCommandWithKey {
    val key: String = creditedAccount
  }

  /**
    * hook command
    *
    * @param uboDeclarationId - ubo declaration id
    * @param status - ubo declaration status
    */
  private[payment] case class UpdateUboDeclarationStatus(uboDeclarationId: String,
                                        status: Option[UboDeclaration.UboDeclarationStatus] = None)
    extends PaymentCommandWithKey{
    lazy val key: String = uboDeclarationId
  }

  /**
    * hook command
    *
    * @param userId - user id
    */
  private[payment] case class ValidateRegularUser(userId: String) extends PaymentCommandWithKey {
    lazy val key: String = userId
  }

  trait PaymentResult extends CommandResult

  case class CardPreRegistered(cardPreRegistration: CardPreRegistration) extends PaymentResult

  case class CardPreAuthorized(transactionId: String) extends PaymentResult

  case class PaidIn(transactionId: String) extends PaymentResult

  case class PaidOut(transactionId: String) extends PaymentResult

  case class Refunded(transactionId: String) extends PaymentResult

  case class Transfered(transferedTransactionId: String, paidOutTransactionId: Option[String] = None) extends PaymentResult

  case class PaymentRedirection(redirectUrl: String) extends PaymentResult

  case class PaymentAccountLoaded(paymentAccount: PaymentAccount) extends PaymentResult

  case object BankAccountCreatedOrUpdated extends PaymentResult

  case class KycDocumentAdded(kycDocumentId: String) extends PaymentResult

  case class KycDocumentStatusUpdated(report: KycDocumentValidationReport) extends PaymentResult

  case class KycDocumentStatusLoaded(report: KycDocumentValidationReport) extends PaymentResult

  case class UboCreatedOrUpdated(ubo: UboDeclaration.UltimateBeneficialOwner) extends PaymentResult

  case object UboDeclarationAskedForValidation extends PaymentResult

  case class UboDeclarationLoaded(declaration: UboDeclaration) extends PaymentResult

  case object UboDeclarationStatusUpdated extends PaymentResult

  case object RegularUserValidated extends PaymentResult

  case class BankAccountLoaded(bankAccount: BankAccount) extends PaymentResult

  case object BankAccountDeleted extends PaymentResult

  case class TransactionLoaded(transaction: Transaction) extends PaymentResult

  case class CardsLoaded(cards: Seq[Card]) extends PaymentResult

  case object CardDisabled extends PaymentResult

  class PaymentError(override val message: String) extends ErrorMessage(message) with PaymentResult

  case object CardNotPreRegistered extends PaymentError("CardNotPreRegistered")

  case object CardNotPreAuthorized extends PaymentError("CardNotPreAuthorized")

  case class CardPreAuthorizationFailed(resultMessage: String) extends PaymentError(resultMessage)

  case class PayInFailed(resultMessage: String) extends PaymentError(resultMessage)

  case class PayOutFailed(resultMessage: String) extends PaymentError(resultMessage)

  case class RefundFailed(resultMessage: String) extends PaymentError(resultMessage)

  case class TransferFailed(resultMessage: String) extends PaymentError(resultMessage)

  case object PaymentAccountNotFound extends PaymentError("PaymentAccountNotFound")

  case object WrongIban extends PaymentError("WrongIban")

  case object WrongBic extends PaymentError("WrongBic")

  case object WrongSiret extends PaymentError("WrongSiret")

  case object WrongOwnerName extends PaymentError("WrongOwnerName")

  case object WrongOwnerAddress extends PaymentError("WrongOwnerAddress")

  case object UserRequired extends PaymentError("UserRequired")

  case object AcceptedTermsOfPSPRequired extends PaymentError("AcceptedTermsOfPSPRequired")

  case object LegalNameRequired extends PaymentError("LegalNameRequired")

  case object WrongLegalRepresentativeAddress extends PaymentError("WrongLegalRepresentativeAddress")

  case object WrongHeadQuartersAddress extends PaymentError("WrongHeadQuartersAddress")

  case object BankAccountNotCreatedOrUpdated extends PaymentError("BankAccountNotCreatedOrUpdated")

  case object KycDocumentNotAdded extends PaymentError("KycDocumentNotAdded")

  case object KycDocumentStatusNotUpdated extends PaymentError("KycDocumentStatusNotUpdated")

  case object KycDocumentStatusNotLoaded extends PaymentError("KycDocumentStatusNotLoaded")

  case object UboNotCreatedOrUpdated extends PaymentError("UboNotCreatedOrUpdated")

  case object UboDeclarationNotAskedForValidation extends PaymentError("UboDeclarationNotAskedForValidation")

  case object UboDeclarationNotFound extends PaymentError("UboDeclarationNotFound")

  case object UboDeclarationStatusNotUpdated extends PaymentError("UboDeclarationStatusNotUpdated")

  case object BankAccountNotFound extends PaymentError("BankAccountNotFound")

  case object BankAccountNotDeleted extends PaymentError("BankAccountNotDeleted")

  case object TransactionNotFound extends PaymentError("TransactionNotFound")

  case object IllegalTransactionStatus extends PaymentError("IllegalTransactionStatus")

  case object IllegalTransactionAmount extends PaymentError("IllegalTransactionAmount")

  case object CardsNotLoaded extends PaymentError("CardsNotLoaded")

  case object CardNotDisabled extends PaymentError("CardNotDisabled")
}
