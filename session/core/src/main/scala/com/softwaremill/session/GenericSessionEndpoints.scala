package com.softwaremill.session

trait GenericSessionEndpoints[T]
    extends OneOffSessionEndpoints[T]
    with RefreshableSessionEndpoints[T]
    with CsrfEndpoints[T] {
  _: SessionTransportEndpoints[T] with SessionContinuityEndpoints[T] with CsrfCheck =>
}

trait GenericCookieSessionEndpoints[T]
    extends GenericSessionEndpoints[T]
    with CookieTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfCheck =>
}

trait GenericHeaderSessionEndpoints[T]
    extends GenericSessionEndpoints[T]
    with HeaderTransportEndpoints[T] {
  _: SessionContinuityEndpoints[T] with CsrfCheck =>
}

trait GenericOneOffCookieSessionEndpoints[T]
    extends GenericCookieSessionEndpoints[T]
    with OneOffSessionContinuity[T] { _: CsrfCheck => }

trait GenericOneOffHeaderSessionEndpoints[T]
    extends GenericHeaderSessionEndpoints[T]
    with OneOffSessionContinuity[T] { _: CsrfCheck => }

trait GenericRefreshableCookieSessionEndpoints[T]
    extends GenericCookieSessionEndpoints[T]
    with RefreshableSessionContinuity[T] { _: CsrfCheck => }

trait GenericRefreshableHeaderSessionEndpoints[T]
    extends GenericHeaderSessionEndpoints[T]
    with RefreshableSessionContinuity[T] { _: CsrfCheck => }
