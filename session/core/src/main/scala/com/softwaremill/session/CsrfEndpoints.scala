package com.softwaremill.session

import sttp.model.Method._
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

private[session] trait CsrfEndpoints[T] extends CsrfCheck {

  import com.softwaremill.session.AkkaToTapirImplicits._

  def manager: SessionManager[T]

  implicit def ec: ExecutionContext

  def csrfCookie: EndpointIO.Header[Option[CookieValueWithMeta]] =
    setCookieOpt(manager.config.csrfCookieConfig.name).description("set csrf token as cookie")

  def submittedCsrfCookie: EndpointInput.Cookie[Option[String]] =
    cookie(manager.config.csrfCookieConfig.name)

  def submittedCsrfHeader: EndpointIO.Header[Option[String]] = header[Option[String]](
    manager.config.csrfSubmittedName
  ).description("read csrf token as header")

  def setNewCsrfToken(): CookieWithMeta = manager.csrfManager.createCookie()

  def hmacTokenCsrfProtectionLogic(
    method: Method,
    csrfTokenFromCookie: Option[String],
    submittedCsrfToken: Option[String]
  ): Either[Unit, (Option[CookieValueWithMeta], Unit)] = {
    csrfTokenFromCookie match {
      case Some(cookie) =>
        val token = cookie
        submittedCsrfToken match {
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

  def hmacTokenCsrfProtection[
    SECURITY_INPUT,
    PRINCIPAL,
    SECURITY_OUTPUT
  ](
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
  ] = {
    val partial =
      // extract csrf token from cookie
      csrfTokenFromCookie()
        // extract request method
        .securityIn(extractFromRequest(req => req.method))
        // extract submitted csrf token from header
        .securityIn(submittedCsrfHeader)
        // extract submitted csrf token from form
        .securityIn(formBody[Map[String, String]])
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
                    submittedCsrfTokenFromForm.get(manager.config.csrfSubmittedName)
                  )(Option(_))
                else
                  submittedCsrfTokenFromHeader
              )
            )
        }
    partial.endpoint
      .prependSecurityIn(body.securityInput)
      .out(body.securityOutput)
      .out(partial.securityOutput)
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
