package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

trait TapirEndpoints extends TapirSession with TapirCsrf {

  def antiCsrfWithRequiredSession[T](
    sc: SessionContinuityEndpoints[T],
    st: GetSessionTransport,
    checkMode: TapirCsrfCheckMode[T]
  ): PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Option[String], Method, Option[String], Map[String, String]),
    T,
    Unit,
    Unit,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    hmacTokenCsrfProtection(checkMode) {
      requiredSession(sc, st)
    }

  def antiCsrfWithOptionalSession[T](
    sc: SessionContinuityEndpoints[T],
    st: GetSessionTransport,
    checkMode: TapirCsrfCheckMode[T]
  ): PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Option[String], Method, Option[String], Map[String, String]),
    Option[T],
    Unit,
    Unit,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    hmacTokenCsrfProtection(checkMode) {
      optionalSession(sc, st)
    }

}
