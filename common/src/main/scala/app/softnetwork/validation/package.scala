package app.softnetwork

import app.softnetwork.specification.Rule

package object validation {

  import scala.util.matching.Regex

  /** Created by smanciot on 25/03/2018.
    */
  /** trait used for validation * */
  trait Validator[T] extends Rule[T] {
    def check(value: T): Boolean

    override def isSatisfiedBy(a: T): Boolean = check(a)
  }

  /** Validator using regex */
  trait RegexValidator extends Validator[String] {
    def regex: Regex
    def check(value: String): Boolean = value match {
      case null => false
      case _    => value.trim.nonEmpty && regex.unapplySeq(value).isDefined
    }
  }

  /** validator for email * */
  object EmailValidator extends RegexValidator {
    val regex: Regex =
      """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r
  }

  /** validator for gsm * */
  object GsmValidator extends RegexValidator {
    val regex: Regex =
      "^\\+?(9[976]\\d|8[987530]\\d|6[987]\\d|5[90]\\d|42\\d|3[875]\\d|2[98654321]\\d|9[8543210]|8[6421]|6[6543210]|5[87654321]|4[987654310]|3[9643210]|2[70]|7|1)\\d{1,14}$".r
  }

}
