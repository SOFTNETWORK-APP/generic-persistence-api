package com.softwaremill.session

import sttp.model.Method
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
    Unit,
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
    partialServerEndpointWithSecurityOutput.endpoint.serverSecurityLogicWithOutput { inputs =>
      partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(inputs).map {
        case Left(l) => Left(l)
        case Right(r) =>
          r._2.toOption match {
            case Some(session) => Right((), session)
            case _             => Left(())
          }
      }
    }
  }

  final def antiCsrfWithOptionalSessionEndpoint: PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Method, Option[String], Option[String]),
    Option[T],
    Unit,
    Unit,
    Unit,
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
    partialServerEndpointWithSecurityOutput.endpoint.serverSecurityLogicWithOutput { inputs =>
      partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(inputs).map {
        case Left(l)  => Left(l)
        case Right(r) => Right((), r._2.toOption)
      }
    }
  }

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
