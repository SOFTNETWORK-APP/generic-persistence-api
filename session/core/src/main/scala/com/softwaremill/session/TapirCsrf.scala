package com.softwaremill.session

import sttp.model.Method._
import sttp.model.headers.CookieValueWithMeta
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.{ExecutionContext, Future}

private[session] trait TapirCsrf[T] { _: CsrfCheck =>

  import com.softwaremill.session.TapirImplicits._

  def manager: SessionManager[T]

  implicit def ec: ExecutionContext

  def csrfCookie: EndpointIO.Header[Option[CookieValueWithMeta]] =
    setCookieOpt(manager.config.csrfCookieConfig.name).description("set csrf token as cookie")

  def submittedCsrfCookie: EndpointInput.Cookie[Option[String]] =
    cookie(manager.config.csrfCookieConfig.name)

  def submittedCsrfHeader: EndpointIO.Header[Option[String]] =
    header[Option[String]](
      manager.config.csrfSubmittedName
    ).description("read csrf token as header")

  def setNewCsrfToken[SECURITY_INPUT, PRINCIPAL, ERROR_OUTPUT, SECURITY_OUTPUT](
    body: => PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      Unit,
      ERROR_OUTPUT,
      SECURITY_OUTPUT,
      Unit,
      Any,
      Future
    ]
  ): PartialServerEndpointWithSecurityOutput[
    SECURITY_INPUT,
    PRINCIPAL,
    Unit,
    ERROR_OUTPUT,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    body.endpoint
      .out(body.securityOutput)
      .out(csrfCookie)
      .serverSecurityLogicWithOutput { inputs =>
        body.securityLogic(new FutureMonad())(inputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            Right(((r._1, Some(manager.csrfManager.createCookie().valueWithMeta)), r._2))
        }
      }

  /** Protects against CSRF attacks using a double-submit cookie. The cookie will be set on any
    * `GET` request which doesn't have the token set in the header. For all other requests, the
    * value of the token from the CSRF cookie must match the value in the custom header (or request
    * body, if `checkFormBody` is `true`).
    *
    * The cookie value is the concatenation of a timestamp and its HMAC hash following the OWASP
    * recommendation for CSRF prevention:
    * @see
    *   <a
    *   href="https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#hmac-based-token-pattern">OWASP</a>
    *
    * Note that this scheme can be broken when not all subdomains are protected or not using HTTPS
    * and secure cookies, and the token is placed in the request body (not in the header).
    *
    * See the documentation for more details.
    */
  def hmacTokenCsrfProtectionLogic(
    method: Method,
    csrfTokenFromCookie: Option[String],
    submittedCsrfToken: Option[String]
  ): Either[Unit, (Option[CookieValueWithMeta], Unit)] = {
    csrfTokenFromCookie match {
      case Some(cookie) =>
        // if a cookie is already set, we let through all get requests (without setting a new token), or validate
        // that the token matches.
        if (method.is(GET)) {
          Right((None, ()))
        } else {
          val token = cookie
          submittedCsrfToken match {
            case Some(submitted) =>
              if (
                submitted == token && token.nonEmpty && manager.csrfManager.validateToken(token)
              ) {
                Right((None, ()))
              } else {
                Left(())
              }
            case _ =>
              Left(())
          }
        }
      // if a cookie is not set, generating a new one for get requests, rejecting other
      case _ =>
        if (method.is(GET)) {
          Right((Some(manager.csrfManager.createCookie().valueWithMeta), ()))
        } else {
          Left(())
        }
    }
  }

  def hmacTokenCsrfProtection[
    SECURITY_INPUT,
    PRINCIPAL,
    ERROR_OUTPUT,
    SECURITY_OUTPUT
  ](
    body: => PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      Unit,
      ERROR_OUTPUT,
      SECURITY_OUTPUT,
      Unit,
      Any,
      Future
    ]
  ): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Option[String], Method, Option[String]),
    PRINCIPAL,
    Unit,
    Unit,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] = {
    val partial =
      // extract csrf token from cookie
      csrfTokenFromCookie()
        // extract request method
        .securityIn(extractFromRequest(req => req.method))
        // extract submitted csrf token from header
        .securityIn(submittedCsrfHeader)
        .out(csrfCookie)
        .errorOut(statusCode(StatusCode.Unauthorized))
        .serverSecurityLogicWithOutput {
          case (
                csrfTokenFromCookie,
                method,
                submittedCsrfTokenFromHeader
              ) =>
            Future.successful(
              hmacTokenCsrfProtectionLogic(
                method,
                csrfTokenFromCookie,
                submittedCsrfTokenFromHeader
              )
            )
        }
    partial.endpoint
      .prependSecurityIn(body.securityInput)
//FIXME      .errorOut(body.errorOutput)
      .out(body.securityOutput.and(partial.securityOutput))
      .serverSecurityLogicWithOutput {
        case (
              securityInput,
              csrfTokenFromCookie,
              method,
              submittedCsrfTokenFromHeader
            ) =>
          partial
            .securityLogic(new FutureMonad())(
              (
                csrfTokenFromCookie,
                method,
                submittedCsrfTokenFromHeader
              )
            )
            .flatMap {
              case Left(l) => Future.successful(Left(l))
              case Right(r) =>
                body.securityLogic(new FutureMonad())(securityInput).map {
                  case Left(l2) => Left(l2)
                  case Right(r2) =>
                    Right(((r2._1, r._1), r2._2))
                }
            }
      }
  }

  implicit def form2Csrf: Map[String, String] => Option[String] = m =>
    m.get(manager.config.csrfSubmittedName)

  def hmacTokenCsrfProtectionWithFormOrMultipart[
    SECURITY_INPUT,
    PRINCIPAL,
    ERROR_OUTPUT,
    SECURITY_OUTPUT,
    F
  ](form: Either[EndpointIO.Body[String, F], EndpointIO.Body[Seq[RawPart], F]])(
    body: => PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      Unit,
      ERROR_OUTPUT,
      SECURITY_OUTPUT,
      Unit,
      Any,
      Future
    ]
  )(implicit f: F => Option[String]): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Option[String], Method, Option[String], F),
    PRINCIPAL,
    Unit,
    Unit,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] = {
    val partial =
      // extract csrf token from cookie
      csrfTokenFromCookie()
        // extract request method
        .securityIn(extractFromRequest(req => req.method))
        // extract submitted csrf token from header
        .securityIn(submittedCsrfHeader)
        // extract submitted csrf token from form
        .securityIn(form.merge) // could not be consumed within the server logic
        .out(csrfCookie)
        .errorOut(statusCode(StatusCode.Unauthorized))
        .serverSecurityLogicWithOutput {
          case (
                csrfTokenFromCookie,
                method,
                submittedCsrfTokenFromHeader,
                submittedCsrfTokenFromForm
              ) =>
            Future.successful(
              hmacTokenCsrfProtectionLogic(
                method,
                csrfTokenFromCookie,
                if (checkHeaderAndForm)
                  submittedCsrfTokenFromHeader.fold(
                    f(submittedCsrfTokenFromForm)
                  )(Option(_))
                else
                  submittedCsrfTokenFromHeader
              )
            )
        }
    partial.endpoint
      .prependSecurityIn(body.securityInput)
      .out(body.securityOutput.and(partial.securityOutput))
      .serverSecurityLogicWithOutput {
        case (
              securityInput,
              csrfTokenFromCookie,
              method,
              submittedCsrfTokenFromHeader,
              submittedCsrfTokenFromForm
            ) =>
          partial
            .securityLogic(new FutureMonad())(
              (
                csrfTokenFromCookie,
                method,
                submittedCsrfTokenFromHeader,
                submittedCsrfTokenFromForm
              )
            )
            .flatMap {
              case Left(l) => Future.successful(Left(l))
              case Right(r) =>
                body.securityLogic(new FutureMonad())(securityInput).map {
                  case Left(l2) => Left(l2)
                  case Right(r2) =>
                    Right(((r2._1, r._1), r2._2))
                }
            }
      }
  }

  def csrfTokenFromCookie(): Endpoint[Option[String], Unit, Unit, Unit, Any] =
    endpoint
      // extract csrf token from cookie
      .securityIn(submittedCsrfCookie)

}

sealed trait CsrfCheck {
  def checkHeaderAndForm: Boolean
}

trait CsrfCheckHeader extends CsrfCheck {
  val checkHeaderAndForm: Boolean = false
}

trait CsrfCheckHeaderAndForm extends CsrfCheck {
  val checkHeaderAndForm: Boolean = true
}
