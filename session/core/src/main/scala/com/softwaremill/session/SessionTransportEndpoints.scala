package com.softwaremill.session

import sttp.tapir.PublicEndpoint
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

sealed trait SessionTransportEndpoints[T] { _: SessionContinuityEndpoints[T] =>
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

  def session(
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

}

trait CookieTransportEndpoints[T] extends SessionTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] =>
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
  _: SessionContinuityEndpoints[T] =>
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
