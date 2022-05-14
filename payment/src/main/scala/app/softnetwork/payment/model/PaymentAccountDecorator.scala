package app.softnetwork.payment.model

trait PaymentAccountDecorator {self: PaymentAccount =>

  lazy val maybeUser: Option[PaymentUser] = {
    if(user.isLegalUser){
      Some(getLegalUser.legalRepresentative)
    }
    else if(user.isNaturalUser){
      Some(getNaturalUser)
    }
    else{
      None
    }
  }

  lazy val externalUuid: String = maybeUser match {
    case Some(user) => user.externalUuid
    case _ => "undefined"
  }

  lazy val userId: Option[String] = maybeUser.flatMap(_.userId)

  lazy val walletId: Option[String] = maybeUser.flatMap(_.walletId)

  lazy val email: Option[String] = maybeUser.map(_.email)

  lazy val emptyUser: Boolean = user.isEmpty

  lazy val legalUser: Boolean = user.isLegalUser

  lazy val legalUserType: Option[LegalUser.LegalUserType] = user.legalUser.map(_.legalUserType)

  def checkIfSameLegalUserType(newlegalUserType: Option[LegalUser.LegalUserType]): Boolean =
    legalUserType.getOrElse(LegalUser.LegalUserType.Unrecognized(-1)) ==
      newlegalUserType.getOrElse(LegalUser.LegalUserType.Unrecognized(-1))

  lazy val documentsValidated: Boolean = documents.forall(_.documentStatus.isKycDocumentValidated)

  lazy val documentOutdated: Boolean = documents.exists(_.documentStatus.isKycDocumentOutOfDate)

  lazy val mandateActivated: Boolean = mandateId.isDefined &&
    mandateStatus.exists(s => s.isMandateActive || s.isMandateSubmitted)

  def resetUserId(userId: Option[String] = None): PaymentAccount = {
    val updatedBankAccount = bankAccount match {
      case Some(s) => Some(s.withUserId(userId.getOrElse("")))
      case _ => None
    }
    if(user.isLegalUser){
      val user = getLegalUser
      self.withLegalUser(user.withLegalRepresentative(user.legalRepresentative.copy(userId = userId))).copy(
        bankAccount = updatedBankAccount
      )
    }
    else if(user.isNaturalUser){
      val user = getNaturalUser
      self.withNaturalUser(user.copy(userId = userId)).copy(
        bankAccount = updatedBankAccount
      )
    }
    else{
      self.copy(
        bankAccount = updatedBankAccount
      )
    }
  }

  def resetWalletId(walletId: Option[String] = None): PaymentAccount = {
    if(user.isLegalUser){
      val user = getLegalUser
      self.withLegalUser(user.withLegalRepresentative(user.legalRepresentative.copy(walletId = walletId)))
    }
    else if(user.isNaturalUser){
      val user = getNaturalUser
      self.withNaturalUser(user.copy(walletId = walletId))
    }
    else{
      self
    }
  }

  def resetBankAccountId(bankAccountId: Option[String] = None): PaymentAccount = {
    self.copy(bankAccount = self.bankAccount.map(_.copy(bankAccountId = bankAccountId)))
  }

  lazy val hasAcceptedTermsOfPSP: Boolean = !legalUser || getLegalUser.lastAcceptedTermsOfPSP.isDefined
}