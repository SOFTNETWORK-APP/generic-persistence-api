package app.softnetwork.payment.persistence.typed

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.kv.handlers.KeyValueDao
import app.softnetwork.kv.persistence.typed.KeyValueBehavior
import app.softnetwork.payment.handlers.{MockPaymentDao, PaymentDao}
import app.softnetwork.payment.message.PaymentEvents._
import app.softnetwork.payment.message.PaymentMessages._
import app.softnetwork.payment.model.LegalUser.LegalUserType
import app.softnetwork.payment.model._
import app.softnetwork.payment.spi._
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed._
import app.softnetwork.serialization.asJson
import org.slf4j.Logger

import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * Created by smanciot on 22/04/2022.
  */
trait PaymentAccountBehavior extends PaymentBehavior[PaymentCommand, PaymentAccount, PaymentEvent, PaymentResult] {
  _: PaymentProvider =>

  override protected val manifestWrapper: ManifestW = ManifestW()

  lazy val keyValueDao: KeyValueDao = KeyValueDao

  lazy val paymentDao: PaymentDao = PaymentDao

  override def init(system: ActorSystem[_])(implicit c: ClassTag[PaymentCommand]): Unit = {
    KeyValueBehavior.init(system)
    super.init(system)
  }

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event    - the event to tag
    * @return event tags
    */
  override protected def tagEvent(entityId: String, event: PaymentEvent): Set[String] =
    event match {
      case _ => super.tagEvent(entityId, event)
    }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(entityId: String, state: Option[PaymentAccount], command: PaymentCommand,
                             replyTo: Option[ActorRef[PaymentResult]], timers: TimerScheduler[PaymentCommand])(
    implicit context: ActorContext[PaymentCommand]
  ): Effect[PaymentEvent, Option[PaymentAccount]] = {
    implicit val system: ActorSystem[_] = context.system
    implicit val log: Logger = context.log
    command match {

      case cmd: PreRegisterCard =>
        import cmd._
        var registerWallet: Boolean = false
        loadPaymentAccount(entityId, user.externalUuid, state, PaymentAccount.User.NaturalUser(user)) match {
          case Some(paymentAccount) =>
            val lastUpdated = now()
            (paymentAccount.userId match {
              case None => createOrUpdatePaymentAccount(Some(paymentAccount.withNaturalUser(user)))
              case some => some
            }) match {
              case Some(userId) =>
                keyValueDao.addKeyValue(userId, entityId)
                (paymentAccount.walletId match {
                  case None =>
                    registerWallet = true
                    createOrUpdateWallet(Some(userId), user.externalUuid, None)
                  case some => some
                }) match {
                  case Some(walletId) =>
                    keyValueDao.addKeyValue(walletId, entityId)
                    val createOrUpdatePaymentAccount =
                      PaymentAccountUpsertedEvent.defaultInstance
                        .withDocument(
                          paymentAccount
                            .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
                            .copy(user = PaymentAccount.User.NaturalUser(
                              user.withUserId(userId).withWalletId(walletId))
                            )
                        )
                        .withLastUpdated(lastUpdated)
                    preRegisterCard(Some(userId), user.externalUuid) match {
                      case Some(cardPreRegistration) =>
                        keyValueDao.addKeyValue(cardPreRegistration.id, entityId)
                        val walletEvents: List[PaymentEvent] =
                          if (registerWallet) {
                            broadcastEvent(
                              WalletRegisteredEvent.defaultInstance
                                .withExternalUuid(user.externalUuid)
                                .withUserId(userId)
                                .withWalletId(walletId)
                                .withLastUpdated(lastUpdated)
                            )
                          }
                          else {
                            List.empty
                          }
                        Effect.persist(
                          broadcastEvent(
                            CardPreRegisteredEvent.defaultInstance
                              .withOrderUuid(orderUuid)
                              .withLastUpdated(lastUpdated)
                              .withExternalUuid(user.externalUuid)
                              .withUserId(userId)
                              .withWalletId(walletId)
                              .withCardPreRegistrationId(cardPreRegistration.id)
                          ) ++ walletEvents :+ createOrUpdatePaymentAccount
                        ).thenRun(_ => CardPreRegistered(cardPreRegistration) ~> replyTo)
                      case _ =>
                        if (registerWallet) {
                          Effect.persist(
                            broadcastEvent(
                              WalletRegisteredEvent.defaultInstance
                                .withExternalUuid(user.externalUuid)
                                .withUserId(userId)
                                .withWalletId(walletId)
                                .withLastUpdated(lastUpdated)
                            ) :+ createOrUpdatePaymentAccount
                          ).thenRun(_ => CardNotPreRegistered ~> replyTo)
                        }
                        else {
                          Effect.persist(
                            createOrUpdatePaymentAccount
                          ).thenRun(_ => CardNotPreRegistered ~> replyTo)
                        }
                    }
                  case _ =>
                    Effect.persist(
                      PaymentAccountUpsertedEvent.defaultInstance
                        .withDocument(
                          paymentAccount.copy(
                            user = PaymentAccount.User.NaturalUser(user.withUserId(userId))
                          )
                        )
                        .withLastUpdated(lastUpdated)
                    ).thenRun(_ => CardNotPreRegistered ~> replyTo)
                }
              case _ =>
                Effect.persist(
                  PaymentAccountUpsertedEvent.defaultInstance
                    .withDocument(paymentAccount.withNaturalUser(user))
                    .withLastUpdated(lastUpdated)
                ).thenRun(_ => CardNotPreRegistered ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => CardNotPreRegistered ~> replyTo)
        }

      case cmd: PreAuthorizeCard =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            paymentAccount.getNaturalUser.userId match {
              case Some(userId) =>
                (cardPreRegistration match {
                  case Some(registration) =>
                    import registration._
                    createCard(id, Some(preregistrationData))
                  case _ => paymentAccount.card.filter(_.active.getOrElse(true)).filterNot(_.expired).map(_.id)
                }) match {
                  case Some(cardId) =>
                    preAuthorizeCard(
                      Some(
                        PreAuthorizationTransaction.defaultInstance
                          .withCardId(cardId)
                          .withAuthorId(userId)
                          .withDebitedAmount(debitedAmount)
                          .withOrderUuid(orderUuid)
                          .copy(
                            ipAddress = ipAddress,
                            browserInfo = browserInfo
                          )
                      )
                    ) match {
                      case Some(transaction) =>
                        handleCardPreAuthorization(
                          entityId,
                          orderUuid,
                          replyTo,
                          paymentAccount,
                          cardPreRegistration.exists(_.registerCard),
                          transaction
                        )
                      case _ => // pre authorization failed
                        Effect.none.thenRun(_ => CardNotPreAuthorized ~> replyTo)
                    }
                  case _ => // no card id
                    Effect.none.thenRun(_ => CardNotPreAuthorized ~> replyTo)
                }
              case _ => // no userId
                Effect.none.thenRun(_ => CardNotPreAuthorized ~> replyTo)
            }
          case _ => // no payment account
            Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: PreAuthorizeCardFor3DS => // 3DS
        import cmd._
        state match {
          case Some(paymentAccount) =>
            loadCardPreAuthorized(orderUuid, preAuthorizationId) match {
              case Some(transaction) =>
                handleCardPreAuthorization(entityId, orderUuid, replyTo, paymentAccount, registerCard, transaction)
              case _ => Effect.none.thenRun(_ => CardNotPreAuthorized ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: PayInWithCardPreAuthorized =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            paymentAccount.transactions.find(_.id == preAuthorizationId) match {
              case None => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
              case Some(transaction) if Seq(
                Transaction.TransactionStatus.TRANSACTION_CREATED,
                Transaction.TransactionStatus.TRANSACTION_SUCCEEDED
              ).contains(transaction.status) =>
                // load credited payment account
                paymentDao.loadPaymentAccount(creditedAccount) complete() match {
                  case Success(s) =>
                    s match {
                      case Some(creditedPaymentAccount) =>
                        creditedPaymentAccount.walletId match {
                          case Some(creditedWalletId) =>
                            payInWithCardPreAuthorized(
                              Some(
                                PayInWithCardPreAuthorizedTransaction.defaultInstance
                                  .withCardPreAuthorizedTransactionId(preAuthorizationId)
                                  .withAuthorId(transaction.authorId)
                                  .withDebitedAmount(transaction.amount)
                                  .withOrderUuid(transaction.orderUuid)
                                  .withCreditedWalletId(creditedWalletId)
                              )
                            ) match {
                              case Some(transaction) =>
                                handlePayIn(
                                  entityId, transaction.orderUuid, replyTo, paymentAccount, registerCard = false, transaction
                                )
                              case _ => Effect.none.thenRun(_ => PayInFailed("unknown") ~> replyTo)
                            }
                          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
                    }
                  case Failure(_) => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => IllegalTransactionStatus ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: PayIn =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            paymentAccount.userId match {
              case Some(userId) =>
                (cardPreRegistration match {
                  case Some(registration) =>
                    import registration._
                    createCard(id, Some(preregistrationData))
                  case _ => paymentAccount.card.filter(_.active.getOrElse(true)).filterNot(_.expired).map(_.id)
                }) match {
                  case Some(cardId) =>
                    // load credited payment account
                    paymentDao.loadPaymentAccount(creditedAccount) complete() match {
                      case Success(s) =>
                        s match {
                          case Some(creditedPaymentAccount) =>
                            creditedPaymentAccount.walletId match {
                              case Some(creditedWalletId) =>
                                payIn(
                                  Some(
                                    PayInTransaction.defaultInstance
                                      .withAuthorId(userId)
                                      .withDebitedAmount(debitedAmount)
                                      .withOrderUuid(orderUuid)
                                      .withCreditedWalletId(creditedWalletId)
                                      .withCardId(cardId)
                                      .copy(
                                        ipAddress = ipAddress,
                                        browserInfo = browserInfo
                                      )
                                  )
                                ) match {
                                  case Some(transaction) =>
                                    val registerCard = cardPreRegistration.exists(_.registerCard)
                                    handlePayIn(
                                      entityId, orderUuid, replyTo, paymentAccount, registerCard, transaction
                                    )
                                  case _ => Effect.none.thenRun(_ => PayInFailed("unknown") ~> replyTo)
                                }
                              case _ => Effect.none.thenRun(_ => PayInFailed("no credited wallet") ~> replyTo)
                            }
                          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
                        }
                      case Failure(_) => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
                    }
                  case _ => Effect.none.thenRun(_ => PayInFailed("no card") ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: PayInFor3DS =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            (paymentAccount.transactions.find(_.id == transactionId) match {
              case None =>
                loadPayIn(orderUuid, transactionId)
              case some => some
            }) match {
              case Some(transaction) =>
                handlePayIn(entityId, orderUuid, replyTo, paymentAccount, registerCard, transaction)
              case _ => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: Refund =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            (paymentAccount.transactions.find(_.id == payInTransactionId) match {
              case None => loadPayIn(orderUuid, payInTransactionId)
              case some => some
            }) match {
              case None => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
              case Some(transaction) if Seq(
                Transaction.TransactionStatus.TRANSACTION_CREATED,
                Transaction.TransactionStatus.TRANSACTION_SUCCEEDED
              ).contains(transaction.status) =>
                if(refundAmount > transaction.amount){
                  Effect.none.thenRun(_ => IllegalTransactionAmount ~> replyTo)
                }
                else {
                  refund(
                    Some(
                      RefundTransaction.defaultInstance
                        .withOrderUuid(orderUuid)
                        .withRefundAmount(refundAmount)
                        .withAuthorId(transaction.authorId)
                        .withReasonMessage(reasonMessage)
                        .withPayInTransactionId(payInTransactionId)
                        .withInitializedByClient(initializedByClient)
                    )
                  ) match {
                    case None => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
                    case Some(transaction) =>
                      keyValueDao.addKeyValue(transaction.id, entityId)
                      val lastUpdated = now()
                      val updatedPaymentAccount = paymentAccount.withTransactions(
                        paymentAccount.transactions.filterNot(_.id == transaction.id)
                          :+ transaction
                      )
                      val upsertedEvent =
                        PaymentAccountUpsertedEvent.defaultInstance
                          .withDocument(updatedPaymentAccount)
                          .withLastUpdated(lastUpdated)
                      transaction.status match {
                        case Transaction.TransactionStatus.TRANSACTION_FAILED_FOR_TECHNICAL_REASON =>
                          log.error("Order-{} could not be refunded: {} -> {}", orderUuid, transaction.id, asJson(transaction))
                          Effect.persist(upsertedEvent).thenRun(_ => RefundFailed(transaction.resultMessage) ~> replyTo)
                        case _ =>
                          if (transaction.status.isTransactionSucceeded || transaction.status.isTransactionCreated) {
                            log.info("Order-{} refunded: {} -> {}", orderUuid, transaction.id, asJson(transaction))
                            Effect.persist(
                              broadcastEvent(
                                RefundedEvent.defaultInstance
                                  .withOrderUuid(orderUuid)
                                  .withLastUpdated(lastUpdated)
                                  .withDebitedAccount(paymentAccount.externalUuid)
                                  .withDebitedAmount(transaction.amount)
                                  .withRefundedAmount(refundAmount)
                                  .withRefundTransactionId(transaction.id)
                                  .withPayInTransactionId(payInTransactionId)
                                  .withReasonMessage(reasonMessage)
                                  .withInitializedByClient(initializedByClient)
                              ) :+ upsertedEvent
                            ).thenRun(_ => Refunded(transaction.id) ~> replyTo)
                          }
                          else{
                            log.info("Order-{} could not be refunded: {} -> {}", orderUuid, transaction.id, asJson(transaction))
                            Effect.persist(upsertedEvent)
                              .thenRun(_ => RefundFailed(transaction.resultMessage) ~> replyTo)
                          }
                      }
                    case _ =>
                      log.error("Order-{} could not be refunded: no transaction returned by provider", orderUuid)
                      Effect.none.thenRun(_ => RefundFailed("no transaction returned by provider") ~> replyTo)
                  }
                }
              case _ => Effect.none.thenRun(_ => IllegalTransactionStatus ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: PayOut =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            paymentAccount.userId match {
              case Some(userId) =>
                paymentAccount.walletId match {
                  case Some(walletId) =>
                    paymentAccount.bankAccount.flatMap(_.bankAccountId) match {
                      case Some(bankAccountId) =>
                        payOut(
                          Some(
                            PayOutTransaction.defaultInstance
                              .withBankAccountId(bankAccountId)
                              .withDebitedAmount(creditedAmount)
                              .withOrderUuid(orderUuid)
                              .withFeesAmount(feesAmount)
                              .withAuthorId(userId)
                              .withCreditedUserId(userId)
                              .withDebitedWalletId(walletId)
                          )
                        ) match {
                          case Some(transaction) =>
                            keyValueDao.addKeyValue(transaction.id, entityId)
                            val lastUpdated = now()
                            val updatedPaymentAccount = paymentAccount.withTransactions(
                              paymentAccount.transactions.filterNot(_.id == transaction.id)
                                :+ transaction
                            )
                            if(transaction.status.isTransactionSucceeded || transaction.status.isTransactionCreated) {
                              log.info("Order-{} paid out : {} -> {}", orderUuid, transaction.id, asJson(transaction))
                              Effect.persist(
                                broadcastEvent(
                                  PaidOutEvent.defaultInstance
                                    .withOrderUuid(orderUuid)
                                    .withLastUpdated(lastUpdated)
                                    .withCreditedAccount(creditedAccount)
                                    .withCreditedAmount(creditedAmount)
                                    .withFeesAmount(feesAmount)
                                    .withTransactionId(transaction.id)
                                ) :+
                                  PaymentAccountUpsertedEvent.defaultInstance
                                    .withDocument(updatedPaymentAccount)
                                    .withLastUpdated(lastUpdated)
                              ).thenRun(_ => PaidOut(transaction.id) ~> replyTo)
                            }
                            else{
                              log.error("Order-{} could not be paid out : {} -> {}", orderUuid, transaction.id, asJson(transaction))
                              Effect.persist(
                                PaymentAccountUpsertedEvent.defaultInstance
                                  .withDocument(updatedPaymentAccount)
                                  .withLastUpdated(lastUpdated)
                              ).thenRun(_ => PayOutFailed(transaction.resultMessage) ~> replyTo)
                            }
                          case _ =>
                            log.error("Order-{} could not be paid out: no transaction returned by provider", orderUuid)
                            Effect.none.thenRun(_ => PayOutFailed("no transaction returned by provider") ~> replyTo)
                        }
                      case _ => Effect.none.thenRun(_ => PayOutFailed("no bank account") ~> replyTo)
                    }
                  case _ => Effect.none.thenRun(_ => PayOutFailed("no wallet id") ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => PayOutFailed("no payment provider user id") ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: Transfer =>
        import cmd._
        state match {
          case Some(paymentAccount) => // debited account
            transfer(paymentAccount.userId match {
              case Some(authorId) =>
                paymentAccount.walletId match {
                  case Some(debitedWalletId) =>
                    // load credited payment account
                    paymentDao.loadPaymentAccount(creditedAccount) complete() match {
                      case Success(s) =>
                        s match {
                          case Some(creditedPaymentAccount) => // credited account
                            creditedPaymentAccount.userId match {
                              case Some(creditedUserId) =>
                                creditedPaymentAccount.walletId match {
                                  case Some(creditedWalletId) =>
                                    Some(
                                      TransferTransaction.defaultInstance
                                        .withDebitedAmount(debitedAmount)
                                        .withFeesAmount(feesAmount)
                                        .withAuthorId(authorId)
                                        .withDebitedWalletId(debitedWalletId)
                                        .withCreditedUserId(creditedUserId)
                                        .withCreditedWalletId(creditedWalletId)
                                    )
                                  case _ => None
                                }
                              case _ => None
                            }
                          case _ => None
                        }
                      case Failure(_) => None
                    }
                  case _ => None
                }
              case _ => None
            }) match {
              case Some(transaction) =>
                val lastUpdated = now()
                val updatedPaymentAccount = paymentAccount.withTransactions(
                  paymentAccount.transactions.filterNot(_.id == transaction.id)
                    :+ transaction
                )
                if(transaction.status.isTransactionSucceeded || transaction.status.isTransactionCreated){
                  val payOutTransactionId =
                    if(payOutRequired){
                      paymentDao.payOut(
                        orderUuid, creditedAccount, debitedAmount, feesAmount = 0/* fees have already been applied */
                      ) complete() match {
                        case Success(s) => s
                        case Failure(_) => None
                      }
                    }
                    else{
                      None
                    }
                  Effect.persist(
                    broadcastEvent(
                      TransferedEvent.defaultInstance
                        .withOrderUuid(orderUuid)
                        .withFeesAmount(feesAmount)
                        .withDebitedAmount(debitedAmount)
                        .withDebitedAccount(debitedAccount)
                        .withLastUpdated(lastUpdated)
                        .withCreditedAccount(creditedAccount)
                        .withTransactionId(transaction.id)
                        .copy(payOutTransactionId = payOutTransactionId)
                    ) :+
                      PaymentAccountUpsertedEvent.defaultInstance
                        .withDocument(updatedPaymentAccount)
                        .withLastUpdated(lastUpdated)
                  ).thenRun(_ => {
                    Transfered(transaction.id, payOutTransactionId)
                  } ~> replyTo)
                }
                else{
                  Effect.persist(
                    PaymentAccountUpsertedEvent.defaultInstance
                      .withDocument(updatedPaymentAccount)
                      .withLastUpdated(lastUpdated)
                  ).thenRun(_ => TransferFailed(transaction.resultMessage) ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case _: LoadPaymentAccount =>
        state match {
          case Some(paymentAccount) => Effect.none.thenRun(_ => PaymentAccountLoaded(paymentAccount) ~> replyTo)
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: LoadTransaction =>
        import cmd._
        state match {
          case Some(paymentAccount) =>
            paymentAccount.transactions.find(_.id == transactionId) match {
              case Some(transaction) => Effect.none.thenRun(_ => TransactionLoaded(transaction) ~> replyTo)
              case _ => Effect.none.thenRun(_ => TransactionNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: CreateOrUpdateBankAccount =>
        if(cmd.bankAccount.wrongIban) {
          Effect.none.thenRun(_ => WrongIban ~> replyTo)
        }
        else if(cmd.bankAccount.wrongBic) {
          Effect.none.thenRun(_ => WrongBic ~> replyTo)
        }
        else if(cmd.bankAccount.wrongOwnerName) {
          Effect.none.thenRun(_ => WrongOwnerName ~> replyTo)
        }
        else if(cmd.bankAccount.wrongOwnerAddress) {
          Effect.none.thenRun(_ => WrongOwnerAddress ~> replyTo)
        }
        else{
          (state match {
            case None =>
              cmd.user match {
                case Some(user) =>
                  loadPaymentAccount(entityId, PaymentAccount.defaultInstance.withUser(user).externalUuid, None, user)
                case _ => None
              }
            case some => some
          }) match {
            case Some(paymentAccount) =>
              import cmd._

              var updatedPaymentAccount =
                paymentAccount
                  .withUser(user.getOrElse(paymentAccount.user))
                  .withBankAccount(bankAccount)

              updatedPaymentAccount.user.legalUser match {
                case Some(legalUser) if legalUser.wrongSiret =>
                  Effect.none.thenRun(_ => WrongSiret ~> replyTo)
                case Some(legalUser) if legalUser.legalName.trim.isEmpty =>
                  Effect.none.thenRun(_ => LegalNameRequired ~> replyTo)
                case Some(legalUser) if legalUser.wrongLegalRepresentativeAddress =>
                  Effect.none.thenRun(_ => WrongLegalRepresentativeAddress ~> replyTo)
                case Some(legalUser) if legalUser.wrongHeadQuartersAddress =>
                  Effect.none.thenRun(_ => WrongHeadQuartersAddress ~> replyTo)
                case Some(legalUser) if legalUser.lastAcceptedTermsOfPSP.isEmpty && !acceptedTermsOfPSP.getOrElse(false) =>
                  Effect.none.thenRun(_ => AcceptedTermsOfPSPRequired ~> replyTo)
                case None if paymentAccount.emptyUser =>
                  Effect.none.thenRun(_ => UserRequired ~> replyTo)
                case _ =>

                  val shouldCreateUser = paymentAccount.emptyUser && !updatedPaymentAccount.emptyUser

                  val shouldUpdateUserType = !shouldCreateUser && {
                    if(paymentAccount.legalUser){ // previous user is a legal user
                      !updatedPaymentAccount.legalUser || // update to natural user
                        !paymentAccount.checkIfSameLegalUserType(updatedPaymentAccount.legalUserType) // update legal user type
                    }
                    else{ // previous user is a natural user
                      updatedPaymentAccount.legalUser // update to legal user
                    }
                  }

                  val shouldUpdateUser = {
                    shouldUpdateUserType ||
                      paymentAccount.maybeUser.map(_.firstName).getOrElse("") !=
                        updatedPaymentAccount.maybeUser.map(_.firstName).getOrElse("") ||
                      paymentAccount.maybeUser.map(_.lastName).getOrElse("") !=
                        updatedPaymentAccount.maybeUser.map(_.lastName).getOrElse("") ||
                      paymentAccount.maybeUser.map(_.birthday).getOrElse("") !=
                        updatedPaymentAccount.maybeUser.map(_.birthday).getOrElse("")
                  }

                  val shouldCreateBankAccount = paymentAccount.bankAccount.isEmpty ||
                    paymentAccount.bankAccount.flatMap(_.bankAccountId).isEmpty

                  val shouldUpdateBankAccount = !shouldCreateBankAccount && (
                    paymentAccount.bankAccount.map(_.ownerName).getOrElse("") != bankAccount.ownerName || // TODO OwnerAddress
                      !paymentAccount.bankAccount.exists(_.checkIfSameIban(bankAccount.iban)) ||
                      !paymentAccount.bankAccount.exists(_.checkIfSameBic(bankAccount.bic)) ||
                      shouldUpdateUser
                    )

                  val shouldCreateOrUpdateBankAccount = shouldCreateBankAccount || shouldUpdateBankAccount

                  val documents: List[KycDocument] = initDocuments(updatedPaymentAccount)

                  val shouldUpdateDocuments = shouldUpdateUser &&
                    documents.exists(!_.documentStatus.isKycDocumentNotSpecified)

                  val shouldCreateUboDeclaration = shouldUpdateUser &&
                    updatedPaymentAccount.getLegalUser.uboDeclarationRequired &&
                    updatedPaymentAccount.getLegalUser.uboDeclaration.isEmpty

                  (paymentAccount.userId match {
                    case None =>
                      createOrUpdatePaymentAccount(Some(updatedPaymentAccount))
                    case Some(_) if shouldUpdateUser =>
                      if(shouldUpdateUserType){
                        createOrUpdatePaymentAccount(Some(updatedPaymentAccount.resetUserId(None)))
                      }
                      else{
                        createOrUpdatePaymentAccount(Some(updatedPaymentAccount))
                      }
                    case some => some
                  }) match {
                    case Some(userId) =>
                      keyValueDao.addKeyValue(userId, entityId)
                      updatedPaymentAccount = updatedPaymentAccount.resetUserId(Some(userId))
                      (paymentAccount.walletId match {
                        case None =>
                          createOrUpdateWallet(Some(userId), updatedPaymentAccount.externalUuid, None)
                        case Some(_) if shouldUpdateUserType =>
                          createOrUpdateWallet(Some(userId), updatedPaymentAccount.externalUuid, None)
                        case some => some
                      }) match {
                        case Some(walletId) =>
                          keyValueDao.addKeyValue(walletId, entityId)
                          updatedPaymentAccount = updatedPaymentAccount.resetWalletId(Some(walletId))
                          (paymentAccount.bankAccount.flatMap(_.bankAccountId) match {
                            case None =>
                              createOrUpdateBankAccount(updatedPaymentAccount.resetBankAccountId().bankAccount)
                            case Some(_) if shouldCreateOrUpdateBankAccount =>
                              createOrUpdateBankAccount(updatedPaymentAccount.resetBankAccountId().bankAccount)
                            case some => some
                          }) match {
                            case Some(bankAccountId) =>
                              keyValueDao.addKeyValue(bankAccountId, entityId)
                              updatedPaymentAccount = updatedPaymentAccount.resetBankAccountId(Some(bankAccountId))

                              var events: List[PaymentEvent] = List.empty

                              val lastUpdated = now()

                              // BankAccountUpdatedEvent
                              events = events ++
                                broadcastEvent(
                                  BankAccountUpdatedEvent.defaultInstance
                                    .withExternalUuid(updatedPaymentAccount.externalUuid)
                                    .withLastUpdated(lastUpdated)
                                    .withUserId(userId)
                                    .withWalletId(walletId)
                                    .withBankAccountId(bankAccountId)
                                )

                              if (acceptedTermsOfPSP.getOrElse(false)) {
                                updatedPaymentAccount = updatedPaymentAccount.withLegalUser(
                                  updatedPaymentAccount.getLegalUser.withLastAcceptedTermsOfPSP(lastUpdated)
                                )
                                // TermsOfPSPAcceptedEvent
                                events = events ++
                                  broadcastEvent(
                                    TermsOfPSPAcceptedEvent.defaultInstance
                                      .withExternalUuid(updatedPaymentAccount.externalUuid)
                                      .withLastUpdated(lastUpdated)
                                      .withLastAcceptedTermsOfPSP(lastUpdated
                                    )
                                  )
                              }

                              if(shouldCreateUboDeclaration){
                                createDeclaration(userId) match {
                                  case Some(uboDeclaration) =>
                                    keyValueDao.addKeyValue(uboDeclaration.uboDeclarationId, entityId)
                                    updatedPaymentAccount = updatedPaymentAccount.withLegalUser(
                                      updatedPaymentAccount.getLegalUser.withUboDeclaration(uboDeclaration)
                                    )
                                    // UboDeclarationUpdatedEvent
                                    events = events ++
                                      broadcastEvent(
                                        UboDeclarationUpdatedEvent.defaultInstance
                                          .withExternalUuid(updatedPaymentAccount.externalUuid)
                                          .withLastUpdated(lastUpdated)
                                          .withUboDeclaration(uboDeclaration)
                                      )
                                  case _ =>
                                    log.warn(s"Could not create ubo declaration for user $userId")
                                }
                              }

                              if(shouldUpdateDocuments){
                                updatedPaymentAccount = updatedPaymentAccount.withDocuments(
                                  documents.map(
                                    _.copy(
                                      lastUpdated = Some(lastUpdated),
                                      documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED,
                                      refusedReasonType = None,
                                      refusedReasonMessage = None
                                    )
                                  )
                                ).withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)

                                // PaymentAccountStatusUpdatedEvent
                                events = events ++
                                  broadcastEvent(
                                    PaymentAccountStatusUpdatedEvent.defaultInstance
                                      .withExternalUuid(updatedPaymentAccount.externalUuid)
                                      .withLastUpdated(lastUpdated)
                                      .withPaymentAccountStatus(updatedPaymentAccount.paymentAccountStatus)
                                  )
                              }
                              else{
                                updatedPaymentAccount = updatedPaymentAccount.withDocuments(documents)
                              }

                              // DocumentsUpdatedEvent
                              events = events ++
                                broadcastEvent(
                                  DocumentsUpdatedEvent.defaultInstance
                                    .withExternalUuid(updatedPaymentAccount.externalUuid)
                                    .withLastUpdated(lastUpdated)
                                    .withDocuments(updatedPaymentAccount.documents)
                                )

                              Effect.persist(events :+
                                PaymentAccountUpsertedEvent.defaultInstance
                                  .withDocument(
                                    updatedPaymentAccount.copy(
                                      bankAccount = updatedPaymentAccount.bankAccount.map(_.encode())
                                    )
                                  )
                                  .withLastUpdated(lastUpdated)
                              )thenRun(_ => BankAccountCreatedOrUpdated ~> replyTo)

                            case _ => Effect.none.thenRun(_ => BankAccountNotCreatedOrUpdated ~> replyTo)
                          }
                        case _ => Effect.none.thenRun(_ => BankAccountNotCreatedOrUpdated ~> replyTo)
                      }
                    case _ => Effect.none.thenRun(_ => BankAccountNotCreatedOrUpdated ~> replyTo)
                  }
              }

            case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
          }
        }

      case cmd: AddKycDocument =>
        import cmd._
        state match {
          case Some(paymentAccount) if paymentAccount.hasAcceptedTermsOfPSP =>
            paymentAccount.userId match {
              case Some(userId) =>
                addDocument(userId, entityId, pages, kycDocumentType) match {
                  case Some(documentId) =>
                    paymentAccount.documents.find(_.documentType == kycDocumentType).flatMap(_.documentId) match {
                      case Some(previous) if previous != documentId =>
                        keyValueDao.removeKeyValue(previous)
                      case _ =>
                    }
                    keyValueDao.addKeyValue(documentId, entityId)

                    val lastUpdated = now()

                    val updatedDocument =
                      paymentAccount.documents.find(_.documentType == kycDocumentType).getOrElse(
                        KycDocument.defaultInstance
                          .withCreatedDate(lastUpdated)
                          .withDocumentType(kycDocumentType)
                      )
                        .withLastUpdated(lastUpdated)
                        .withDocumentId(documentId)
                        .withDocumentStatus(KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
                        .copy(
                          refusedReasonType = None,
                          refusedReasonMessage = None
                        )

                    val newDocuments = paymentAccount.documents.filterNot(_.documentType == kycDocumentType) :+
                      updatedDocument

                    Effect.persist(
                      broadcastEvent(
                        DocumentsUpdatedEvent.defaultInstance
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withLastUpdated(lastUpdated)
                          .withDocuments(newDocuments)
                      ) ++ broadcastEvent(
                        DocumentUpdatedEvent.defaultInstance
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withLastUpdated(lastUpdated).withDocument(updatedDocument)
                      ) :+ PaymentAccountUpsertedEvent.defaultInstance
                        .withDocument(paymentAccount.withDocuments(newDocuments))
                        .withLastUpdated(lastUpdated)
                    ).thenRun(_ => KycDocumentAdded(documentId) ~> replyTo)

                  case _ => Effect.none.thenRun(_ => KycDocumentNotAdded ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => KycDocumentNotAdded ~> replyTo)
            }
          case None => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
          case _ => Effect.none.thenRun(_ => AcceptedTermsOfPSPRequired ~> replyTo)
        }

      case cmd: UpdateKycDocumentStatus =>
        import cmd._
        state match {
          case Some(paymentAccount)  =>
            paymentAccount.documents.find(_.documentId.getOrElse("") == kycDocumentId) match {
              case Some(document) =>
                val documentStatusUpdated =
                  updateDocumentStatus(
                    paymentAccount,
                    document,
                    kycDocumentId,
                    status
                  )
                Effect.persist(
                  documentStatusUpdated._2
                ).thenRun(_ => KycDocumentStatusUpdated(documentStatusUpdated._1) ~> replyTo)
              case _ => Effect.none.thenRun(_ => KycDocumentStatusNotUpdated ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: LoadKycDocumentStatus =>
        state match {
          case Some(paymentAccount) =>
            import cmd._
            paymentAccount.documents.find(_.documentType == kycDocumentType) match {
              case Some(document) =>
                if(document.documentStatus.isKycDocumentValidationAsked) {
                  val documentStatusUpdated =
                    updateDocumentStatus(
                      paymentAccount,
                      document,
                      document.documentId.getOrElse(""),
                      None
                    )
                  Effect.persist(
                    documentStatusUpdated._2
                  ).thenRun(_ => KycDocumentStatusLoaded(documentStatusUpdated._1) ~> replyTo)
                }
                else {
                  Effect.none.thenRun(_ => KycDocumentStatusLoaded(
                    KycDocumentValidationReport.defaultInstance
                      .withUserId(paymentAccount.userId.getOrElse(""))
                      .withDocumentId(document.documentId.getOrElse(""))
                      .withStatus(document.documentStatus)
                      .copy(
                        refusedReasonType = document.refusedReasonType,
                        refusedReasonMessage = document.refusedReasonMessage
                      )
                  ) ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => KycDocumentStatusNotLoaded ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: CreateOrUpdateUbo =>
        state match {
          case Some(paymentAccount) =>
            var declarationCreated: Boolean = false
            (paymentAccount.getLegalUser.uboDeclaration match {
              case None =>
                createDeclaration(paymentAccount.userId.getOrElse("")) match {
                  case Some(declaration) =>
                    declarationCreated = true
                    keyValueDao.addKeyValue(declaration.uboDeclarationId, entityId)
                    Some(declaration)
                  case _ => None
                }
              case some => some
            }) match {
              case Some(declaration) =>
                import cmd._
                var events: List[PaymentEvent] = List.empty

                val lastUpdated = now()

                if(declarationCreated){
                  events = events ++
                    broadcastEvent(
                      UboDeclarationUpdatedEvent.defaultInstance
                        .withExternalUuid(paymentAccount.externalUuid)
                        .withLastUpdated(lastUpdated)
                        .withUboDeclaration(declaration)
                    )
                }

                createOrUpdateUBO(paymentAccount.userId.getOrElse(""), declaration.uboDeclarationId, ubo) match {
                  case Some(ubo) =>
                    Effect.persist(events :+
                      PaymentAccountUpsertedEvent.defaultInstance
                        .withDocument(
                          paymentAccount.withLegalUser(
                            paymentAccount.getLegalUser
                              .withUboDeclaration(
                                declaration.withUbos(declaration.ubos.filterNot(_.id == ubo.id) :+ ubo)
                              )
                          )
                        )
                        .withLastUpdated(lastUpdated)
                    ).thenRun(_ => UboCreatedOrUpdated(ubo) ~> replyTo)

                  case _ =>
                    Effect.persist(events).thenRun(_ => UboNotCreatedOrUpdated ~> replyTo)
                }

              case _ => Effect.none.thenRun(_ => UboNotCreatedOrUpdated ~> replyTo)
            }

          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case _: ValidateUboDeclaration =>
        state match {
          case Some(paymentAccount) =>
            paymentAccount.getLegalUser.uboDeclaration match {
              case Some(uboDeclaration) if uboDeclaration.status.isUboDeclarationCreated ||
                uboDeclaration.status.isUboDeclarationIncomplete =>
                validateDeclaration(paymentAccount.userId.getOrElse(""), uboDeclaration.uboDeclarationId) match {
                  case Some(declaration) =>
                    val updatedUbo = declaration.withUbos(uboDeclaration.ubos)
                    val lastUpdated = now()
                    Effect.persist(
                      broadcastEvent(
                        UboDeclarationUpdatedEvent.defaultInstance
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withLastUpdated(lastUpdated)
                          .withUboDeclaration(updatedUbo)
                      ) :+
                        PaymentAccountUpsertedEvent.defaultInstance
                          .withDocument(
                            paymentAccount.withLegalUser(paymentAccount.getLegalUser.withUboDeclaration(updatedUbo))
                          )
                          .withLastUpdated(lastUpdated)
                    ).thenRun(_ => UboDeclarationAskedForValidation ~> replyTo)
                  case _ => Effect.none.thenRun(_ => UboDeclarationNotAskedForValidation ~> replyTo)
                }
              case _ => Effect.none.thenRun(_ => UboDeclarationNotAskedForValidation ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case _: GetUboDeclaration =>
        state match {
          case Some(paymentAccount) =>
            paymentAccount.getLegalUser.uboDeclaration match {
              case Some(uboDeclaration) => Effect.none.thenRun(_ => UboDeclarationLoaded(uboDeclaration) ~> replyTo)
              case _ => Effect.none.thenRun(_ => UboDeclarationNotLoaded ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case cmd: UpdateUboDeclarationStatus =>
        state match {
          case Some(paymentAccount) =>
            paymentAccount.getLegalUser.uboDeclaration match {
              case Some(uboDeclaration) if uboDeclaration.status.isUboDeclarationValidationAsked =>
                import cmd._
                getDeclaration(paymentAccount.userId.getOrElse(""), uboDeclarationId) match {
                  case Some(declaration) if declaration.uboDeclarationId == uboDeclarationId =>
                    val internalStatus = {
                      if (environment != "prod") {
                        status.getOrElse(declaration.status)
                      }
                      else {
                        declaration.status
                      }
                    }
                    var events: List[PaymentEvent] = List.empty
                    val lastUpdated = now()
                    var updatedPaymentAccount = paymentAccount
                      .withLegalUser(paymentAccount.getLegalUser.withUboDeclaration(declaration.withStatus(internalStatus)))
                    events = events ++
                      broadcastEvent(
                        UboDeclarationUpdatedEvent.defaultInstance
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withLastUpdated(lastUpdated)
                          .withUboDeclaration(declaration)
                      )
                    if(internalStatus.isUboDeclarationIncomplete || internalStatus.isUboDeclarationRefused){
                      events = events ++
                        broadcastEvent(
                          PaymentAccountStatusUpdatedEvent.defaultInstance
                            .withExternalUuid(paymentAccount.externalUuid)
                            .withLastUpdated(lastUpdated)
                            .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
                        )
                      updatedPaymentAccount = updatedPaymentAccount
                        .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
                    }
                    else if(internalStatus.isUboDeclarationValidated && paymentAccount.documentsValidated){
                      events = events ++
                        broadcastEvent(
                          PaymentAccountStatusUpdatedEvent.defaultInstance
                            .withExternalUuid(paymentAccount.externalUuid)
                            .withLastUpdated(lastUpdated)
                            .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
                        )
                      updatedPaymentAccount = updatedPaymentAccount
                        .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
                    }
                    Effect.persist(events :+
                      PaymentAccountUpsertedEvent.defaultInstance
                        .withDocument(updatedPaymentAccount)
                        .withLastUpdated(lastUpdated)
                    ).thenRun(_ => UboDeclarationStatusUpdated ~> replyTo)
                  case _ =>
                    Effect.none.thenRun(_ => UboDeclarationStatusNotUpdated ~> replyTo)
                }
              case _ =>
                Effect.none.thenRun(_ => UboDeclarationStatusNotUpdated ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case _: DeleteUboDeclaration =>
        state match {
          case Some(paymentAccount) =>
            paymentAccount.getLegalUser.uboDeclaration match {
              case Some(uboDeclaration) =>
                keyValueDao.removeKeyValue(uboDeclaration.uboDeclarationId)
                val lastUpdated = now()
                Effect.persist(
                    broadcastEvent(
                      UboDeclarationUpdatedEvent.defaultInstance
                        .withExternalUuid(paymentAccount.externalUuid)
                        .withLastUpdated(lastUpdated)
                        .clearUboDeclaration
                    ) :+ PaymentAccountUpsertedEvent.defaultInstance
                      .withDocument(
                        paymentAccount.withLegalUser(paymentAccount.getLegalUser.copy(uboDeclaration = None))
                      )
                      .withLastUpdated(lastUpdated)
                ).thenRun(_ => UboDeclarationDeleted ~> replyTo)

              case _ => Effect.none.thenRun(_ => UboDeclarationNotDeleted ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case _: ValidateRegularUser =>
        state match {
          case Some(paymentAccount) =>
            val lastUpdated = now()

            var events: List[PaymentEvent] =
              broadcastEvent(
                PaymentAccountStatusUpdatedEvent.defaultInstance
                  .withExternalUuid(paymentAccount.externalUuid)
                  .withLastUpdated(lastUpdated)
                  .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
              )

            var updatedPaymentAccount = paymentAccount
              .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
            updatedPaymentAccount.getLegalUser.uboDeclaration match {
              case Some(uboDeclaration) =>
                val declaration =
                  uboDeclaration.withStatus(UboDeclaration.UboDeclarationStatus.UBO_DECLARATION_VALIDATED)
                updatedPaymentAccount = updatedPaymentAccount.withLegalUser(
                  updatedPaymentAccount.getLegalUser.withUboDeclaration(declaration)
                )
                events = events ++
                  broadcastEvent(
                    UboDeclarationUpdatedEvent.defaultInstance
                      .withExternalUuid(paymentAccount.externalUuid)
                      .withLastUpdated(lastUpdated)
                      .withUboDeclaration(declaration)
                  )
              case _ =>
            }

            if(!updatedPaymentAccount.documentsValidated){
              updatedPaymentAccount = updatedPaymentAccount.withDocuments(
                updatedPaymentAccount.documents.map(_.withLastUpdated(lastUpdated)
                  .copy(
                    documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATED
                  )
                )
              )
              events = events ++
                broadcastEvent(
                  DocumentsUpdatedEvent.defaultInstance
                    .withExternalUuid(paymentAccount.externalUuid)
                    .withLastUpdated(lastUpdated)
                    .withDocuments(updatedPaymentAccount.documents)
                )
            }

            Effect.persist(events :+
              PaymentAccountUpsertedEvent.defaultInstance
                .withDocument(updatedPaymentAccount)
                .withLastUpdated(lastUpdated)
            ).thenRun(_ => RegularUserValidated ~> replyTo)

          case _ => Effect.none.thenRun(_ => PaymentAccountNotFound ~> replyTo)
        }

      case _: LoadBankAccount   =>
        state match {
          case Some(paymentAccount) =>
            Effect.none.thenRun(_ => (paymentAccount.bankAccount match {
              case Some(bank) => BankAccountLoaded(bank)
              case _ => BankAccountNotFound
            }) ~> replyTo)
          case _ => Effect.none.thenRun(_ => BankAccountNotFound ~> replyTo)
        }

      case _: DeleteBankAccount =>
        state match {
          case Some(paymentAccount) =>
            paymentAccount.bankAccount match {
              case Some(bankAccount) =>
                bankAccount.bankAccountId match {
                  case Some(bankAccountId) =>
                    keyValueDao.removeKeyValue(bankAccountId)
                  case _ =>
                }
                val lastUpdated = now()
                var updatedPaymentAccount = paymentAccount.copy(bankAccount = None)
                var events: List[PaymentEvent] = {
                  broadcastEvent(
                    BankAccountDeletedEvent.defaultInstance
                      .withExternalUuid(paymentAccount.externalUuid)
                      .withLastUpdated(lastUpdated)
                  )
                }
                updatedPaymentAccount.getLegalUser.uboDeclaration match {
                  case Some(declaration) =>
                    keyValueDao.removeKeyValue(declaration.uboDeclarationId)
                    updatedPaymentAccount = updatedPaymentAccount
                      .withLegalUser(updatedPaymentAccount.getLegalUser.copy(uboDeclaration = None))
                    events = events ++
                      broadcastEvent(
                        UboDeclarationUpdatedEvent.defaultInstance
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withLastUpdated(lastUpdated)
                          .clearUboDeclaration
                      )
                  case _ =>
                }

                updatedPaymentAccount = updatedPaymentAccount.withDocuments(
                  updatedPaymentAccount.documents.map(_.copy(
                    documentId = None,
                    lastUpdated = Some(lastUpdated),
                    documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED)
                  )
                ).withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
                events = events ++
                  broadcastEvent(
                    DocumentsUpdatedEvent.defaultInstance
                      .withExternalUuid(paymentAccount.externalUuid)
                      .withLastUpdated(lastUpdated)
                      .withDocuments(updatedPaymentAccount.documents)
                  ) ++
                  broadcastEvent(
                    PaymentAccountStatusUpdatedEvent.defaultInstance
                      .withExternalUuid(paymentAccount.externalUuid)
                      .withLastUpdated(lastUpdated)
                      .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
                  )

                Effect.persist(events :+
                  PaymentAccountUpsertedEvent.defaultInstance
                    .withDocument(updatedPaymentAccount)
                    .withLastUpdated(lastUpdated)
                ).thenRun(_ => BankAccountDeleted ~> replyTo)
              case _ => Effect.none.thenRun(_ => BankAccountNotDeleted ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => BankAccountNotFound ~> replyTo)
        }

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to handle
    * @return new state
    */
  override def handleEvent(state: Option[PaymentAccount], event: PaymentEvent)(
    implicit context: ActorContext[_]): Option[PaymentAccount] =
    event match {
      case _ => super.handleEvent(state, event)
    }

  private[this] def loadPaymentAccount(entityId: String, uuid: String, state: Option[PaymentAccount], user: PaymentAccount.User)(implicit system: ActorSystem[_], log: Logger): Option[PaymentAccount] = {
    state match {
      case None =>
        keyValueDao.lookupKeyValue(uuid) complete() match {
          case Success(s) =>
            s match {
              case Some(t) if t != entityId =>
                log.warn(s"another payment account entity $t has already been associated with this uuid $uuid")
                None
              case _ =>
                keyValueDao.addKeyValue(uuid, entityId)
                Some(PaymentAccount.defaultInstance.withUuid(entityId).withUser(user))
            }
          case Failure(f) =>
            log.error(f.getMessage, f)
            None
        }
      case Some(paymentAccount) =>
        if(paymentAccount.externalUuid != uuid){
          log.warn(s"the payment account entity $entityId has already been associated with another uuid ${paymentAccount.externalUuid}")
          None
        }
        else{
          keyValueDao.addKeyValue(uuid, entityId)
          Some(paymentAccount)
        }
    }
  }

  private[this] def handlePayIn(entityId: String,
                                orderUuid: String,
                                replyTo: Option[ActorRef[PaymentResult]],
                                paymentAccount: PaymentAccount,
                                registerCard: Boolean,
                                transaction: Transaction
                               )(implicit system: ActorSystem[_], log: Logger
  ): Effect[PaymentEvent, Option[PaymentAccount]] = {
    keyValueDao.addKeyValue(transaction.id, entityId) // add transaction id as a key for this payment account
    val lastUpdated = now()
    var updatedPaymentAccount =
      paymentAccount.withTransactions(
        paymentAccount.transactions
          .filterNot(_.id == transaction.id) :+ transaction
      )
    transaction.status match {
      case Transaction.TransactionStatus.TRANSACTION_CREATED if transaction.redirectUrl.isDefined => // 3ds
        Effect.persist(
          PaymentAccountUpsertedEvent.defaultInstance
            .withDocument(updatedPaymentAccount)
            .withLastUpdated(lastUpdated)
        ).thenRun(_ =>
          PaymentRedirection(
            transaction.redirectUrl.get
          ) ~> replyTo
        )
      case _ =>
        if(transaction.status.isTransactionSucceeded || transaction.status.isTransactionCreated) {
          log.debug("Order-{} paid in: {} -> {}", orderUuid, transaction.id, asJson(transaction))
          val registerCardEvents: List[PaymentEvent] =
            if(registerCard){
              transaction.cardId match {
                case Some(cardId) =>
                  loadCard(cardId) match {
                    case Some(card) =>
                      val updatedCard = updatedPaymentAccount.maybeUser match {
                        case Some(user) =>
                          card.withFirstName(user.firstName).withLastName(user.lastName).withBirthday(user.birthday)
                        case _ => card
                      }
                      updatedPaymentAccount = updatedPaymentAccount.withCard(updatedCard)
                      broadcastEvent(
                        CardRegisteredEvent.defaultInstance
                          .withOrderUuid(orderUuid)
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withCard(updatedCard)
                          .withLastUpdated(lastUpdated)
                      )
                    case _ => List.empty
                  }
                case _ => List.empty
              }
            }
            else{
              List.empty
            }
          Effect.persist(
            registerCardEvents ++
              broadcastEvent(
                PaidInEvent.defaultInstance
                  .withOrderUuid(orderUuid)
                  .withTransactionId(transaction.id)
                  .withDebitedAccount(paymentAccount.externalUuid)
                  .withDebitedAmount(transaction.amount)
                  .withLastUpdated(lastUpdated)
              ) :+
              PaymentAccountUpsertedEvent.defaultInstance
                .withDocument(updatedPaymentAccount)
                .withLastUpdated(lastUpdated)
          ).thenRun(_ => PaidIn(transaction.id) ~> replyTo)
        }
        else {
          log.error("Order-{} could not be paid in: {} -> {}", orderUuid, transaction.id, asJson(transaction))
          Effect.persist(
            PaymentAccountUpsertedEvent.defaultInstance
              .withDocument(updatedPaymentAccount)
              .withLastUpdated(lastUpdated)
          ).thenRun(_ =>
            PayInFailed(transaction.resultMessage) ~> replyTo
          )
        }
    }
  }

  private[this] def handleCardPreAuthorization(entityId: String,
                                               orderUuid: String,
                                               replyTo: Option[ActorRef[PaymentResult]],
                                               paymentAccount: PaymentAccount,
                                               registerCard: Boolean,
                                               transaction: Transaction)(
                                                implicit system: ActorSystem[_], log: Logger): Effect[PaymentEvent, Option[PaymentAccount]] = {
    keyValueDao.addKeyValue(transaction.id, entityId) // add transaction id as a key for this payment account
    val lastUpdated = now()
    var updatedPaymentAccount =
      paymentAccount.withTransactions(
        paymentAccount.transactions
          .filterNot(_.id == transaction.id) :+ transaction
      )
    transaction.status match {
      case Transaction.TransactionStatus.TRANSACTION_CREATED if transaction.redirectUrl.isDefined => // 3ds
        Effect.persist(
          PaymentAccountUpsertedEvent.defaultInstance
            .withDocument(updatedPaymentAccount)
            .withLastUpdated(lastUpdated)
        ).thenRun(_ =>
          PaymentRedirection(
            transaction.redirectUrl.get
          ) ~> replyTo
        )
      case _ =>
        if(transaction.status.isTransactionSucceeded || transaction.status.isTransactionCreated) {
          log.debug("Order-{} pre authorized: {} -> {}", orderUuid, transaction.id, asJson(transaction))
          val registerCardEvents: List[PaymentEvent] =
            if(registerCard){
              transaction.cardId match {
                case Some(cardId) =>
                  loadCard(cardId) match {
                    case Some(card) =>
                      val updatedCard = updatedPaymentAccount.maybeUser match {
                        case Some(user) =>
                          card.withFirstName(user.firstName).withLastName(user.lastName).withBirthday(user.birthday)
                        case _ => card
                      }
                      updatedPaymentAccount = updatedPaymentAccount.withCard(updatedCard)
                      broadcastEvent(
                        CardRegisteredEvent.defaultInstance
                          .withOrderUuid(orderUuid)
                          .withExternalUuid(paymentAccount.externalUuid)
                          .withCard(updatedCard)
                          .withLastUpdated(lastUpdated)
                      )
                    case _ => List.empty
                  }
                case _ => List.empty
              }
            }
            else{
              List.empty
            }
          Effect.persist(
            registerCardEvents ++
              broadcastEvent(
                CardPreAuthorizedEvent.defaultInstance
                  .withOrderUuid(orderUuid)
                  .withTransactionId(transaction.id)
                  .withDebitedAccount(paymentAccount.externalUuid)
                  .withDebitedAmount(transaction.amount)
                  .withLastUpdated(lastUpdated)
              ) :+
              PaymentAccountUpsertedEvent.defaultInstance
                .withDocument(updatedPaymentAccount)
                .withLastUpdated(lastUpdated)
          ).thenRun(_ => CardPreAuthorized(transaction.id) ~> replyTo)
        }
        else {
          log.error("Order-{} could not be pre authorized: {} -> {}", orderUuid, transaction.id, asJson(transaction))
          Effect.persist(
            PaymentAccountUpsertedEvent.defaultInstance
              .withDocument(updatedPaymentAccount)
              .withLastUpdated(lastUpdated)
          ).thenRun(_ =>
            CardPreAuthorizationFailed(transaction.resultMessage) ~> replyTo
          )
        }
    }
 }

  private[this] def updateDocumentStatus(paymentAccount: PaymentAccount,
                                         document: KycDocument,
                                         documentId: String,
                                         maybeStatus: Option[KycDocument.KycDocumentStatus] = None
                                         )(implicit system: ActorSystem[_]): (
    KycDocumentValidationReport, List[PaymentEvent]) = {
    var events: List[PaymentEvent] = List.empty
    val lastUpdated = now()

    val userId = paymentAccount.userId.getOrElse("")

    val report = loadDocumentStatus(userId, documentId)

    val internalStatus =
      if(environment != "prod"){
        maybeStatus.getOrElse(report.status)
      }
      else {
        report.status
      }

    val updatedDocument =
      document
        .withLastUpdated(lastUpdated)
        .withDocumentStatus(internalStatus).copy(
        refusedReasonType = report.refusedReasonType,
        refusedReasonMessage = report.refusedReasonMessage
      )

    events = events ++
      broadcastEvent(
        DocumentUpdatedEvent.defaultInstance
          .withExternalUuid(paymentAccount.externalUuid)
          .withLastUpdated(lastUpdated)
          .withDocument(updatedDocument)
      )

    val newDocuments =
      paymentAccount.documents.filterNot(_.documentId.getOrElse("") == documentId) :+ updatedDocument

    var updatedPaymentAccount = paymentAccount.withDocuments(newDocuments)

    events = events ++
      broadcastEvent(
        DocumentsUpdatedEvent.defaultInstance
          .withExternalUuid(paymentAccount.externalUuid)
          .withLastUpdated(lastUpdated)
          .withDocuments(newDocuments)
      )

    if(updatedPaymentAccount.documentsValidated && paymentAccount.getLegalUser.uboDeclarationValidated){
      if(!paymentAccount.paymentAccountStatus.isCompteOk){
        events = events ++
          broadcastEvent(
            PaymentAccountStatusUpdatedEvent.defaultInstance
              .withExternalUuid(paymentAccount.externalUuid)
              .withLastUpdated(lastUpdated)
              .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
          )
        updatedPaymentAccount = updatedPaymentAccount
          .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.COMPTE_OK)
      }
    }
    else if(internalStatus.isKycDocumentRefused){
      events = events ++
        broadcastEvent(
          PaymentAccountStatusUpdatedEvent.defaultInstance
            .withExternalUuid(paymentAccount.externalUuid)
            .withLastUpdated(lastUpdated)
            .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
        )
      updatedPaymentAccount = updatedPaymentAccount
        .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
    }
    else if(internalStatus.isKycDocumentOutOfDate && !paymentAccount.documentOutdated){
      events = events ++
        broadcastEvent(
          PaymentAccountStatusUpdatedEvent.defaultInstance
            .withExternalUuid(paymentAccount.externalUuid)
            .withLastUpdated(lastUpdated)
            .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
        )
      updatedPaymentAccount = updatedPaymentAccount
        .withPaymentAccountStatus(PaymentAccount.PaymentAccountStatus.DOCUMENTS_KO)
    }

    (report, events :+
      PaymentAccountUpsertedEvent.defaultInstance
        .withDocument(updatedPaymentAccount)
        .withLastUpdated(lastUpdated)
    )
  }

  private[this] def initDocuments(paymentAccount: PaymentAccount): List[KycDocument] = {
    var newDocuments: List[KycDocument] = paymentAccount.documents.toList
    newDocuments =
      List(
        newDocuments.find(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF).getOrElse(
          KycDocument.defaultInstance.copy(
            documentType = KycDocument.KycDocumentType.KYC_IDENTITY_PROOF,
            documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED
          )
        )
      ) ++ newDocuments.filterNot(_.documentType == KycDocument.KycDocumentType.KYC_IDENTITY_PROOF)
      paymentAccount.legalUserType match {
        case Some(lpt) => lpt match {
          case LegalUserType.SOLETRADER =>
            newDocuments =
              List(
                newDocuments.find(_.documentType == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF).getOrElse(
                  KycDocument.defaultInstance.copy(
                    documentType = KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
                    documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED
                  )
                )
              ) ++ newDocuments.filterNot(_.documentType == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF)
          case LegalUserType.BUSINESS =>
            newDocuments =
              List(
                newDocuments.find(_.documentType == KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF).getOrElse(
                  KycDocument.defaultInstance.copy(
                    documentType = KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
                    documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED
                  )
                ),
                newDocuments.find(_.documentType == KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION).getOrElse(
                  KycDocument.defaultInstance.copy(
                    documentType = KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION,
                    documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED
                  )
                ),
                newDocuments.find(_.documentType == KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION).getOrElse(
                  KycDocument.defaultInstance.copy(
                    documentType = KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION,
                    documentStatus = KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED
                  )
                )
              ) ++ newDocuments.filterNot(
                d => Seq(
                  KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
                  KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION,
                  KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION
                ).contains(d.documentType)
              )
          case _ =>
        }

        case _ =>
          newDocuments = newDocuments.filterNot(
            d => Seq(
              KycDocument.KycDocumentType.KYC_REGISTRATION_PROOF,
              KycDocument.KycDocumentType.KYC_ARTICLES_OF_ASSOCIATION,
              KycDocument.KycDocumentType.KYC_SHAREHOLDER_DECLARATION
            ).contains(d.documentType)
          )
      }

    newDocuments
  }
}

case object PaymentAccountBehavior extends PaymentAccountBehavior with MangoPayProvider

case object MockPaymentAccountBehavior extends PaymentAccountBehavior with MockMangoPayProvider{
  override def persistenceId = s"Mock${super.persistenceId}"
  override lazy val paymentDao: PaymentDao = MockPaymentDao
}
