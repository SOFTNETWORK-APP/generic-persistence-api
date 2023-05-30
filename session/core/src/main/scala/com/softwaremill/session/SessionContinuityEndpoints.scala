package com.softwaremill.session

import sttp.tapir.PublicEndpoint
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

sealed trait SessionContinuityEndpoints[T] {
  def setCookieSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    _,
    T,
    INPUT,
    Unit,
    _,
    Unit,
    Any,
    Future
  ]

  def setHeaderSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    _,
    T,
    INPUT,
    Unit,
    _,
    Unit,
    Any,
    Future
  ]

  def cookieSession(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future]

  def headerSession(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future]

  def invalidateSession[
    SECURITY_INPUT,
    PRINCIPAL,
    SECURITY_OUTPUT
  ](
    partial: PartialServerEndpointWithSecurityOutput[
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
    _,
    PRINCIPAL,
    Unit,
    Unit,
    _,
    Unit,
    Any,
    Future
  ]
}

trait OneOffSessionContinuity[T] extends SessionContinuityEndpoints[T] {
  _: OneOffSessionEndpoints[T] =>
  override def setCookieSession[INPUT](endpoint: PublicEndpoint[INPUT, Unit, Unit, Any])(implicit
    f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[_, T, INPUT, Unit, _, Unit, Any, Future] =
    setOneOffCookieSession(endpoint)

  override def setHeaderSession[INPUT](endpoint: PublicEndpoint[INPUT, Unit, Unit, Any])(implicit
    f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[_, T, INPUT, Unit, _, Unit, Any, Future] =
    setOneOffHeaderSession(endpoint)

  override def cookieSession(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = oneOffCookieSession(required)

  override def headerSession(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = oneOffHeaderSession(required)

  override def invalidateSession[SECURITY_INPUT, PRINCIPAL, SECURITY_OUTPUT](
    partial: PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      Unit,
      Unit,
      SECURITY_OUTPUT,
      Unit,
      Any,
      Future
    ]
  ): PartialServerEndpointWithSecurityOutput[_, PRINCIPAL, Unit, Unit, _, Unit, Any, Future] =
    invalidateOneOffSession(partial)
}

trait RefreshableSessionContinuity[T] extends SessionContinuityEndpoints[T] {
  _: RefreshableSessionEndpoints[T] =>
  override def setCookieSession[INPUT](endpoint: PublicEndpoint[INPUT, Unit, Unit, Any])(implicit
    f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[_, T, INPUT, Unit, _, Unit, Any, Future] =
    setRefreshableCookieSession(endpoint)

  override def setHeaderSession[INPUT](endpoint: PublicEndpoint[INPUT, Unit, Unit, Any])(implicit
    f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[_, T, INPUT, Unit, _, Unit, Any, Future] =
    setRefreshableHeaderSession(endpoint)

  override def cookieSession(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = refreshableCookieSession(required)

  override def headerSession(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = refreshableHeaderSession(required)

  override def invalidateSession[SECURITY_INPUT, PRINCIPAL, SECURITY_OUTPUT](
    partial: PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      Unit,
      Unit,
      SECURITY_OUTPUT,
      Unit,
      Any,
      Future
    ]
  ): PartialServerEndpointWithSecurityOutput[_, PRINCIPAL, Unit, Unit, _, Unit, Any, Future] =
    invalidateRefreshableSession(partial)
}
