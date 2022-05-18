package app.softnetwork.payment.model

import app.softnetwork.validation.RegexValidator

import scala.util.matching.Regex

trait LegalUserDecorator {self: LegalUser =>
  lazy val  wrongSiret: Boolean = !SiretValidator.check(siret)

  lazy val wrongLegalRepresentativeAddress: Boolean = legalRepresentativeAddress.wrongAddress

  lazy val wrongHeadQuartersAddress: Boolean = headQuartersAddress.wrongAddress

  lazy val uboDeclarationRequired: Boolean = legalUserType.isBusiness

  lazy val uboDeclarationValidated: Boolean = !uboDeclarationRequired ||
    uboDeclaration.exists(_.status.isUboDeclarationValidated)

}

object SiretValidator extends RegexValidator {
  val regex: Regex = """^[0-9]{14}""".r
}
