package app.softnetwork.persistence.auth.config

import java.util.regex.Matcher

import org.passay._

/**
  * Created by smanciot on 11/04/2018.
  */
object Password {

  sealed trait PasswordRule {
    def rule: Rule
  }

  case class PasswordRules(
                            length: Option[Length] = None,
                            upperCaseCharacter: Option[UpperCaseCharacter] = None,
                            lowerCaseCharacter: Option[LowerCaseCharacter] = None,
                            numberCharacter: Option[NumberCharacter] = None,
                            allowedRegex: Option[AllowedRegex] = None,
                            allowedCharacter: Option[AllowedCharacter] = None,
                            whitespace: Option[Whitespace] = None,
                            specialCharacter: Option[SpecialCharacter] = None
                          ){

    private lazy val rules = Seq(
      length,
      upperCaseCharacter,
      lowerCaseCharacter,
      numberCharacter,
      allowedRegex,
      allowedCharacter,
      whitespace,
      specialCharacter
    ).flatMap(rule => rule match {
      case Some(s) => Some(s.rule)
      case _       => None
    })

    def validate(password: String): Either[Seq[String], Boolean] = {
      import scala.collection.JavaConverters._
      val validator = new PasswordValidator(rules: _*)
      val result = validator.validate(new PasswordData(password))
      if (result.isValid)
        Right(true)
      else
        Left(result.getDetails.asScala.map(_.getErrorCode))
    }
  }

  case class Length(min: Int = 8, max: Int = 16) extends PasswordRule{
    override lazy val rule: Rule = new LengthRule(min, max)
  }

  class ExtendedAllowedRegexRule(
                                  val regex: String,
                                  val errorCode: String = AllowedRegexRule.ERROR_CODE
                                ) extends AllowedRegexRule(regex){
    override def validate(passwordData: PasswordData): RuleResult = {
      val result: RuleResult = new RuleResult(true)
      val m: Matcher = pattern.matcher(passwordData.getPassword)
      if (!m.find) {
        result.setValid(false)
        result.getDetails.add(new RuleResultDetail(errorCode, createRuleResultDetailParameters))
      }
      result
    }
  }

  case class AllowedRegex(regex: String, errorCode: String = AllowedRegexRule.ERROR_CODE) extends PasswordRule {
    override lazy val rule: Rule = new ExtendedAllowedRegexRule(regex, errorCode)
  }

  class UpperCaseCharacter(val size: Int = 1) extends AllowedRegex(
    "\\w*" + (for(_ <- 1 to size) yield "[A-Z]\\w*").mkString(""),
    "UPPER_CASE_CHARACTER"
  ){
    assert(size > 0, "the size must be at least greater than or equal to 1")
  }

  class LowerCaseCharacter(val size: Int = 1) extends AllowedRegex(
    "\\w*" + (for(_ <- 1 to size) yield "[a-z]\\w*").mkString(""),
    "LOWER_CASE_CHARACTER"
  ){
    assert(size > 0, "the size must be at least greater than or equal to 1")
  }

  class NumberCharacter(val size: Int = 1) extends AllowedRegex(
    "\\w*" + (for(_ <- 1 to size) yield "[0-9]\\w*").mkString(""),
    "NUMBER_CHARACTER"
  ){
    assert(size > 0, "the size must be at least greater than or equal to 1")
  }

  class SpecialCharacter(val size: Int = 1) extends AllowedRegex(
    "\\w*" + (for(_ <- 1 to size) yield "[^A-Za-z0-9]\\w*").mkString(""),
    "SPECIAL_CHARACTER"
  ){
    assert(size > 0, "the size must be at least greater than or equal to 1")
  }

  case class AllowedCharacter(chars: String) extends PasswordRule {
    override lazy val rule: Rule = new AllowedCharacterRule(chars.toCharArray)
  }

  case class Whitespace() extends PasswordRule {
    override lazy val rule: Rule = new WhitespaceRule()
  }

}
