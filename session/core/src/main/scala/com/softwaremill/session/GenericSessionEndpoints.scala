package com.softwaremill.session

import sttp.model.Method
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

trait GenericSessionEndpoints[T] extends CsrfEndpoints[T] {
  _: SessionTransportEndpoints[T] with SessionContinuityEndpoints[T] with CsrfCheck =>

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

trait GenericCookieSessionEndpoints[T] extends GenericSessionEndpoints[T] with CookieTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfCheck =>
}

trait GenericHeaderSessionEndpoints[T] extends GenericSessionEndpoints[T] with HeaderTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfCheck =>
}

trait GenericOneOffCookieSessionEndpoints[T]
  extends GenericCookieSessionEndpoints[T]
    with OneOffSessionContinuity[T]
    with OneOffSessionEndpoints[T] {
  _: CsrfCheck =>
}

trait GenericOneOffHeaderSessionEndpoints[T]
  extends GenericHeaderSessionEndpoints[T]
    with OneOffSessionContinuity[T]
    with OneOffSessionEndpoints[T] {
  _: CsrfCheck =>
}

trait GenericRefreshableCookieSessionEndpoints[T]
  extends GenericCookieSessionEndpoints[T]
    with RefreshableSessionContinuity[T]
    with RefreshableSessionEndpoints[T]
    with OneOffSessionEndpoints[T] {
  _: CsrfCheck =>
}

trait GenericRefreshableHeaderSessionEndpoints[T]
  extends GenericHeaderSessionEndpoints[T]
    with RefreshableSessionContinuity[T]
    with RefreshableSessionEndpoints[T]
    with OneOffSessionEndpoints[T] {
  _: CsrfCheck =>
}
