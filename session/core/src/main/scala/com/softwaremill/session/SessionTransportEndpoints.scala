package com.softwaremill.session

import sttp.model.Method
import sttp.monad.FutureMonad
import sttp.tapir.PublicEndpoint
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

sealed trait SessionTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfEndpoints[T] =>
  def setSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    _,
    Unit,
    INPUT,
    Unit,
    _,
    Unit,
    Any,
    Future
  ]

  private[session] def session(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future]

  final def requiredSession
    : PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
      T
    ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = session(Some(true))

  final def optionalSession
    : PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
      T
    ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = session(Some(false))

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

trait CookieTransportEndpoints[T] extends SessionTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfEndpoints[T] =>
  override def setSession[INPUT](endpoint: PublicEndpoint[INPUT, Unit, Unit, Any])(implicit
    f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[_, Unit, INPUT, Unit, _, Unit, Any, Future] =
    setCookieSession(endpoint)

  override def session(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = cookieSession(required)
}

trait HeaderTransportEndpoints[T] extends SessionTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfEndpoints[T] =>
  override def setSession[INPUT](endpoint: PublicEndpoint[INPUT, Unit, Unit, Any])(implicit
    f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[_, Unit, INPUT, Unit, _, Unit, Any, Future] =
    setHeaderSession(endpoint)

  override def session(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = headerSession(required)
}

// TODO trait CookieOrHeaderTransportEndpoints[T] extends SessionTransportEndpoints[T] { _: SessionContinuityEndpoints[T] => }
