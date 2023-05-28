package com.softwaremill.session

import app.softnetwork.concurrent.Completion
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir.{
  cookie,
  header,
  setCookieOpt,
  statusCode,
  EndpointIO,
  EndpointInput,
  PublicEndpoint
}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait RefreshableSessionEndpoints[T] extends Completion {
  this: OneOffSessionEndpoints[T] =>
  import com.softwaremill.session.AkkaToTapirImplicits._

  implicit def refreshTokenStorage: RefreshTokenStorage[T]

  implicit def ec: ExecutionContext

  def refreshable: Refreshable[T] = SessionOptions.refreshable

  def getRefreshTokenFromClientAsCookie: EndpointInput.Cookie[Option[String]] = {
    cookie(manager.config.refreshTokenCookieConfig.name)
  }

  def sendRefreshTokenToClientAsCookie: EndpointIO.Header[Option[CookieValueWithMeta]] = {
    setCookieOpt(manager.config.refreshTokenCookieConfig.name)
  }

  def getRefreshTokenFromClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.refreshTokenHeaderConfig.getFromClientHeaderName)
  }

  def sendRefreshTokenToClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.refreshTokenHeaderConfig.sendToClientHeaderName)
  }

  private[session] def rotateToken(
    v: T,
    existing: Option[String]
  ): Option[String] = {
    refreshable.refreshTokenManager
      .rotateToken(v, existing) complete () match {
      case Success(value) => Some(value)
      case Failure(_)     => None
    }
  }

  implicit def refreshTokenToCookie(refreshToken: Option[String]): Option[CookieValueWithMeta] =
    refreshToken.map(refreshable.refreshTokenManager.createCookie(_).valueWithMeta)

  private[session] def setRefreshableSessionLogic[INPUT](
    input: INPUT,
    existing: Option[String]
  )(implicit f: INPUT => Option[T]): Either[Unit, (Option[String], Unit)] =
    implicitly[Option[T]](input) match {
      case Some(v) => Right(rotateToken(v, existing), ())
      case _       => Left(())
    }

  def setRefreshableCookieSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    (INPUT, Option[String], Option[String]),
    Unit,
    INPUT,
    Unit,
    (Option[CookieValueWithMeta], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] = {
    val partialServerEndpointWithSecurityOutput = setOneOffCookieSession(endpoint)
    partialServerEndpointWithSecurityOutput.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .serverSecurityLogicWithOutput { case (input, cookie, existing) =>
        partialServerEndpointWithSecurityOutput
          .securityLogic(new FutureMonad())(input, cookie)
          .map {
            case Left(_) =>
              setRefreshableSessionLogic(input, existing)
                .map(result => ((None, result._1), result._2))
            case Right(r) =>
              setRefreshableSessionLogic(input, existing)
                .map(result => ((r._1, result._1), result._2))
          }
      }
  }

  def setRefreshableHeaderSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    (INPUT, Option[String], Option[String]),
    Unit,
    INPUT,
    Unit,
    (Option[String], Option[String]),
    Unit,
    Any,
    Future
  ] = {
    val partialServerEndpointWithSecurityOutput = setOneOffHeaderSession(endpoint)
    partialServerEndpointWithSecurityOutput.endpoint
      .securityIn(getRefreshTokenFromClientAsHeader)
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .out(sendRefreshTokenToClientAsHeader)
      .serverSecurityLogicWithOutput { case (input, header, existing) =>
        partialServerEndpointWithSecurityOutput
          .securityLogic(new FutureMonad())(input, header)
          .map {
            case Left(_) =>
              setRefreshableSessionLogic(input, existing)
                .map(result => ((None, result._1), result._2))
            case Right(r) =>
              setRefreshableSessionLogic(input, existing)
                .map(result => ((r._1, result._1), result._2))
          }
      }
  }

  private[session] def refreshTokenLogic(
    refreshToken: Option[String],
    required: Option[Boolean]
  ): Either[Unit, (Option[String], SessionResult[T])] =
    refreshToken match {
      case Some(value) =>
        refreshable.refreshTokenManager
          .sessionFromValue(value) complete () match {
          case Success(value) =>
            value match {
              case s @ SessionResult.CreatedFromToken(session) =>
                Right(rotateToken(session, refreshToken), s)
              case s => Right(None, s)
            }
          case Failure(_) =>
            if (required.getOrElse(false))
              Left(())
            else
              Right(None, SessionResult.NoSession)
        }
      case _ =>
        if (required.getOrElse(false))
          Left(())
        else
          Right(None, SessionResult.NoSession)
    }

  private[session] def refreshableSessionLogic(
    session: Option[SessionResult[T]],
    refreshToken: Option[String],
    required: Option[Boolean]
  ): Either[Unit, (Option[String], SessionResult[T])] =
    session match {
      case Some(result) =>
        result match {
          case SessionResult.NoSession | SessionResult.Expired =>
            refreshTokenLogic(refreshToken, required)
          case s => Right(refreshToken, s)
        }
      case _ => refreshTokenLogic(refreshToken, required)
    }

  def refreshableCookieSession(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = {
    val partialServerEndpointWithSecurityOutput = oneOffCookieSession(required)
    partialServerEndpointWithSecurityOutput.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .mapSecurityIn(inputs => inputs._1 :+ inputs._2)(seq => (seq.reverse.tail, seq.last))
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .mapOut(outputs => outputs._1 :+ outputs._2.map(_.value))(seq => (seq.reverse.tail, seq.last))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        val cookie = inputs.head
        val refreshToken = inputs.last
        partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(Seq(cookie)).map {
          case Left(_) =>
            refreshableSessionLogic(None, refreshToken, required)
              .map(result => (Seq(None, result._1), result._2))
          case Right(r) =>
            refreshableSessionLogic(Some(r._2), refreshToken, required)
              .map(result => (r._1 :+ result._1, result._2))
        }
      }
  }

  def refreshableHeaderSession(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = {
    val partialServerEndpointWithSecurityOutput = oneOffHeaderSession(required)
    partialServerEndpointWithSecurityOutput.endpoint
      .securityIn(getRefreshTokenFromClientAsHeader)
      .mapSecurityIn(inputs => inputs._1 :+ inputs._2)(seq => (seq.reverse.tail, seq.last))
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .out(sendRefreshTokenToClientAsHeader)
      .mapOut(outputs => outputs._1 :+ outputs._2)(seq => (seq.reverse.tail, seq.last))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        val header = inputs.head
        val refreshToken = inputs.last
        partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(Seq(header)).map {
          case Left(_) =>
            refreshableSessionLogic(None, refreshToken, required)
              .map(result => (Seq(None, result._1), result._2))
          case Right(r) =>
            refreshableSessionLogic(Some(r._2), refreshToken, required)
              .map(result => (r._1 :+ result._1, result._2))
        }
      }
  }

  //TODO def refreshableCookieOrHeaderSession(required: Option[Boolean] = None) = {}

  private[session] def invalidateRefreshableSessionLogic(
    cookie: Option[String],
    header: Option[String]
  ): Either[Nothing, ((Option[CookieValueWithMeta], Option[String]), Unit)] =
    cookie match {
      case Some(c) =>
        refreshable.refreshTokenManager.removeToken(c) complete () match {
          case _ =>
            val deleted = refreshable.refreshTokenManager.createCookie("").valueWithMeta
            header match {
              case Some(_) => Right((Some(deleted), Some("")), ())
              case _       => Right((Some(deleted), None), ())
            }
        }
      case _ =>
        header match {
          case Some(h) =>
            refreshable.refreshTokenManager.removeToken(h) complete () match {
              case _ => Right((None, Some("")), ())
            }
          case _ => Right((None, None), ())
        }
    }

  def invalidateRefreshableSession[
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
    (SECURITY_INPUT, Option[String], Option[String], Option[String], Option[String]),
    Unit,
    Unit,
    Unit,
    (Option[CookieValueWithMeta], Option[String], Option[CookieValueWithMeta], Option[String]),
    Unit,
    Any,
    Future
  ] = {
    val partialServerEndpointWithSecurityOutput = invalidateOneOffSession(partial)
    partialServerEndpointWithSecurityOutput.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .securityIn(getRefreshTokenFromClientAsHeader)
      .out(partialServerEndpointWithSecurityOutput.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .out(sendRefreshTokenToClientAsHeader)
      .serverSecurityLogicWithOutput { case (si, sc, sh, cookie, header) =>
        partialServerEndpointWithSecurityOutput.securityLogic(new FutureMonad())(si, sc, sh).map {
          case Left(_) =>
            invalidateRefreshableSessionLogic(cookie, header)
              .map(result => ((None, None, result._1._1, result._1._2), ()))
          case Right(r) =>
            invalidateRefreshableSessionLogic(cookie, header)
              .map(result => ((r._1._1, r._1._2, result._1._1, result._1._2), ()))
        }
      }
  }
}
