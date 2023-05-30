package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.CookieValueWithMeta
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

trait GenericSessionEndpoints[T] extends CsrfEndpoints[T] {
  _: SessionTransportEndpoints[T] with SessionContinuityEndpoints[T] =>

  final def antiCsrfWithRequiredSession: PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Method, Option[String], Option[String]),
    T,
    Unit,
    Unit,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] = {
    val partialServerEndpointWithSecurityOutput =
      // check anti CSRF token
      hmacTokenCsrfProtectionEndpoint(
        // check if a session exists
        requiredSession
      )
    partialServerEndpointWithSecurityOutput.endpoint
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(inputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            r._2.toOption match {
              case Some(session) => Right(r._1, session)
              case _             => Left(())
            }
        }
      }
  }

  final def antiCsrfWithOptionalSession: PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Method, Option[String], Option[String]),
    Option[T],
    Unit,
    Unit,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] = {
    val partialServerEndpointWithSecurityOutput =
      // check anti CSRF token
      hmacTokenCsrfProtectionEndpoint(
        // optional session
        optionalSession
      )
    partialServerEndpointWithSecurityOutput.endpoint
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(inputs).map {
          case Left(l)  => Left(l)
          case Right(r) => Right(r._1, r._2.toOption)
        }
      }
  }

  def transport: SessionTransportEndpoints[T] = this

  def continuity: SessionContinuityEndpoints[T] = this
}

trait GenericOneOffCookieSessionEndpoints[T]
    extends GenericSessionEndpoints[T]
    with CookieTransportEndpoints[T]
    with OneOffSessionContinuity[T]
    with OneOffSessionEndpoints[T]

trait GenericOneOffHeaderSessionEndpoints[T]
    extends GenericSessionEndpoints[T]
    with HeaderTransportEndpoints[T]
    with OneOffSessionContinuity[T]
    with OneOffSessionEndpoints[T]

trait GenericRefreshableCookieSessionEndpoints[T]
    extends GenericSessionEndpoints[T]
    with CookieTransportEndpoints[T]
    with RefreshableSessionContinuity[T]
    with RefreshableSessionEndpoints[T]
    with OneOffSessionEndpoints[T]

trait GenericRefreshableHeaderSessionEndpoints[T]
    extends GenericSessionEndpoints[T]
    with HeaderTransportEndpoints[T]
    with RefreshableSessionContinuity[T]
    with RefreshableSessionEndpoints[T]
    with OneOffSessionEndpoints[T]
