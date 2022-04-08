package app.softnetwork.persistence.auth.model

import java.security.SecureRandom
import java.util.{Date, Calendar}

import app.softnetwork.specification.{Specification, Rule}

import scala.language.reflectiveCalls

import app.softnetwork.persistence._

/**
  * Created by smanciot on 14/04/2018.
  */
trait ExpirationDate {

  def compute(expiryTimeInMinutes: Int): Date = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, expiryTimeInMinutes)
    cal.getTime
  }

}

trait VerificationExpirationDate {
  def expirationDate: Date
  final def expired = Specification(ExpirationDateRule).isSatisfiedBy(this)
}

case object ExpirationDateRule extends Rule[VerificationExpirationDate]{
  override def isSatisfiedBy(a: VerificationExpirationDate): Boolean =
    a.expirationDate.getTime < now().getTime
}

trait VerificationTokenCompanion extends ExpirationDate {

  def apply(login: String, expiryTimeInMinutes: Int): VerificationToken = {
    VerificationToken(BearerTokenGenerator.generateSHAToken(login), compute(expiryTimeInMinutes))
  }

}

trait VerificationCodeCompanion extends ExpirationDate {

  def apply(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    VerificationCode(
      s"%0${pinSize}d".format(new SecureRandom().nextInt(math.pow(10, pinSize).toInt)),
      compute(expiryTimeInMinutes)
    )
  }

}
