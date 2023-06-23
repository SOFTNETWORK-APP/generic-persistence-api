package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.tapir._
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.{ExecutionContext, Future}

trait CsrfEndpoints {

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
  def hmacTokenCsrfProtection[T, SECURITY_INPUT, PRINCIPAL, SECURITY_OUTPUT](
    checkMode: TapirCsrfCheckMode[T]
  )(
    body: => PartialServerEndpointWithSecurityOutput[
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
    (SECURITY_INPUT, Option[String], Method, Option[String], Map[String, String]),
    PRINCIPAL,
    Unit,
    Unit,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    checkMode.hmacTokenCsrfProtection {
      body
    }

  def csrfCookie[T](
    checkMode: TapirCsrfCheckMode[T]
  ): EndpointIO.Header[Option[CookieValueWithMeta]] =
    checkMode.csrfCookie

  def csrfTokenFromCookie[T](
    checkMode: TapirCsrfCheckMode[T]
  ): Endpoint[Option[String], Unit, Unit, Unit, Any] =
    checkMode.csrfTokenFromCookie()

  def setNewCsrfToken[T](
    checkMode: TapirCsrfCheckMode[T]
  ): CookieWithMeta =
    checkMode.setNewCsrfToken()
}

object CsrfEndpoints extends CsrfEndpoints

object TapirCsrfOptions {
  def checkHeader[T](implicit manager: SessionManager[T], ec: ExecutionContext) =
    new TapirCsrfCheckHeader[T]()
  def checkHeaderAndForm[T](implicit manager: SessionManager[T], ec: ExecutionContext) =
    new TapirCsrfCheckHeaderAndForm[T]()
}

sealed trait TapirCsrfCheckMode[T] extends TapirCsrf[T] {
  def manager: SessionManager[T]
  def ec: ExecutionContext
  def csrfManager: CsrfManager[T] = manager.csrfManager
}

class TapirCsrfCheckHeader[T](implicit val manager: SessionManager[T], val ec: ExecutionContext)
    extends TapirCsrfCheckMode[T]
    with CsrfCheckHeader

class TapirCsrfCheckHeaderAndForm[T](implicit
  val manager: SessionManager[T],
  val ec: ExecutionContext
) extends TapirCsrfCheckMode[T]
    with CsrfCheckHeaderAndForm
