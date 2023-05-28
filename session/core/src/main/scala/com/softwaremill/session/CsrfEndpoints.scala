package com.softwaremill.session

import sttp.model.Method._
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir.{EndpointIO, _}

import scala.concurrent.{ExecutionContext, Future}

trait CsrfEndpoints[T] extends CsrfCheck {

  import com.softwaremill.session.AkkaToTapirImplicits._

  def manager: SessionManager[T]

  implicit def ec: ExecutionContext

  /** Protects against CSRF attacks using a double-submit cookie. The cookie will be set on any
    * `GET` request which doesn't have the token set in the header. For all other requests, the
    * value of the token from the CSRF cookie must match the value in the custom header (TODO or
    * request body, if `checkFormBody` is `true`).
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
  def hmacTokenCsrfProtection(
    method: Method,
    csrfTokenCookie: Option[String],
    csrfTokenAsHeader: Option[String],
    csrfTokenAsForm: Option[String]
  ): Either[Unit, (Option[CookieValueWithMeta], Unit)] = {
    csrfTokenCookie match {
      case Some(cookie) =>
        val token = cookie
        csrfTokenAsHeader.fold(csrfTokenAsForm)(Option(_)) match {
          case Some(submitted) =>
            if (submitted == token && token.nonEmpty && manager.csrfManager.validateToken(token)) {
              Right((None, ()))
            } else {
              Left(())
            }
          case _ =>
            Left(())
        }
      // if a cookie is not set, generating a new one for **get** requests, rejecting other
      case _ =>
        if (method.is(GET)) {
          Right((Some(manager.csrfManager.createCookie().valueWithMeta), ()))
        } else {
          Left(())
        }
    }
  }

  def submittedCsrfCookie: EndpointInput.Cookie[Option[String]] =
    cookie(manager.config.csrfCookieConfig.name)

  def csrfCookie: EndpointIO.Header[Option[CookieValueWithMeta]] =
    setCookieOpt(manager.config.csrfCookieConfig.name).description("set csrf token as cookie")

  def submittedCsrfHeader: EndpointIO.Header[Option[String]] = header[Option[String]](
    manager.config.csrfSubmittedName
  ).description("read csrf token as header")

  def setNewCsrfToken(): CookieWithMeta = manager.csrfManager.createCookie()

  def hmacTokenCsrfProtectionEndpoint[
    SECURITY_INPUT,
    PRINCIPAL,
    SECURITY_OUTPUT
  ](
    partialServerEndpointWithSecurityOutput: PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      Unit,
      Unit,
      SECURITY_OUTPUT,
      Unit,
      Any,
      Future
    ]
  ): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Method, Option[String], Option[String] /*, Option[Map[String, String]]*/ ),
    PRINCIPAL,
    Unit,
    Unit,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    partialServerEndpointWithSecurityOutput.endpoint
      // extract request method
      .securityIn(extractFromRequest(req => req.method))
      // extract csrf cookie
      .securityIn(submittedCsrfCookie)
      // extract token from header
      .securityIn(submittedCsrfHeader)
//      // extract token from form
//      .securityIn(formBody[Map[String, String]])
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .out(csrfCookie)
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { case (si, method, cookie, header) =>
        partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(si).map {
          case Left(l) => Left(l)
          case Right(r) =>
            hmacTokenCsrfProtection(
              method,
              cookie,
              header,
              /*if (checkHeaderAndForm) form.get(manager.config.csrfSubmittedName) else*/ None
            ).map(result => ((r._1, result._1), r._2))
        }
      }

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
