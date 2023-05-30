package com.softwaremill.session

import akka.http.scaladsl.model.DateTime
import sttp.model.{Header, StatusCode}
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir.{cookie, header, _}

import scala.concurrent.{ExecutionContext, Future}

trait OneOffSessionEndpoints[T] {
  import AkkaToTapirImplicits._

  implicit def manager: SessionManager[T]

  implicit def ec: ExecutionContext

  def getSessionFromClientAsCookie: EndpointInput.Cookie[Option[String]] = {
    cookie(manager.config.sessionCookieConfig.name)
  }

  def sendSessionToClientAsCookie: EndpointIO.Header[Option[CookieValueWithMeta]] = {
    setCookieOpt(manager.config.sessionCookieConfig.name)
  }

  def getSessionFromClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.sessionHeaderConfig.getFromClientHeaderName)
  }

  def sendSessionToClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.sessionHeaderConfig.sendToClientHeaderName)
  }

  private[session] def setOneOffCookieSessionLogic[INPUT](
    input: INPUT,
    cookie: Option[String]
  )(implicit f: INPUT => Option[T]): Either[Unit, (Some[CookieValueWithMeta], T)] =
    implicitly[Option[T]](input) match {
      case Some(v) =>
        cookie match {
          case Some(value) =>
            Right(
              Some(manager.clientSessionManager.createCookieWithValue(value).valueWithMeta),
              v
            )
          case _ =>
            val cookie: CookieWithMeta = manager.clientSessionManager.createCookie(v)
            Right(Some(cookie.valueWithMeta), v)
        }
      case _ => Left(())
    }

  def setOneOffCookieSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    (INPUT, Option[String]),
    T,
    INPUT,
    Unit,
    Option[CookieValueWithMeta],
    Unit,
    Any,
    Future
  ] =
    endpoint
      .securityIn(endpoint.input)
      .securityIn(getSessionFromClientAsCookie)
      .out(sendSessionToClientAsCookie)
      .serverSecurityLogicWithOutput { case (input, cookie) =>
        Future.successful(setOneOffCookieSessionLogic(input, cookie))
      }

  private[session] def setOneOffHeaderSessionLogic[INPUT](
    input: INPUT,
    header: Option[String]
  )(implicit f: INPUT => Option[T]): Either[Unit, (Some[String], T)] =
    implicitly[Option[T]](input) match {
      case Some(v) =>
        header match {
          case Some(value) =>
            Right(Some(value), v)
          case _ =>
            val header: Header = manager.clientSessionManager.createHeader(v)
            Right(Some(header.value), v)
        }
      case _ => Left(())
    }

  def setOneOffHeaderSession[INPUT](
    endpoint: PublicEndpoint[INPUT, Unit, Unit, Any]
  )(implicit f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[
    (INPUT, Option[String]),
    T,
    INPUT,
    Unit,
    Option[
      String
    ],
    Unit,
    Any,
    Future
  ] =
    endpoint
      .securityIn(endpoint.input)
      .securityIn(getSessionFromClientAsHeader)
      .out(sendSessionToClientAsHeader)
      .serverSecurityLogicWithOutput { case (input, header) =>
        Future.successful(setOneOffHeaderSessionLogic(input, header))
      }

  private[session] def oneOffCookieSessionLogic(
    cookie: Option[String],
    required: Option[Boolean]
  ): Either[Unit, (Option[CookieValueWithMeta], SessionResult[T])] = {
    oneOffCookieOrHeaderSessionLogic(cookie, None, required).map(e => (e._1._1, e._2))
  }

  def oneOffCookieSession(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] = {
    endpoint
      .securityIn(getSessionFromClientAsCookie)
      .mapSecurityIn(Seq(_))(_.head)
      .out(sendSessionToClientAsCookie)
      .mapOut(o => Seq(o.map(_.value)))(oo =>
        oo.head.map(manager.clientSessionManager.createCookieWithValue(_).valueWithMeta)
      )
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(oneOffCookieSessionLogic(inputs.head, required) match {
          case Left(l)  => Left(l)
          case Right(r) => Right(Seq(r._1.map(_.value)), r._2)
        })
      }
  }

  private[session] def oneOffHeaderSessionLogic(
    header: Option[String],
    required: Option[Boolean]
  ): Either[Unit, (Option[String], SessionResult[T])] = {
    oneOffCookieOrHeaderSessionLogic(None, header, required).map(e => (e._1._2, e._2))
  }

  def oneOffHeaderSession(
    required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]], SessionResult[
    T
  ], Unit, Unit, Seq[Option[String]], Unit, Any, Future] =
    endpoint
      .securityIn(
        getSessionFromClientAsHeader
      )
      .mapSecurityIn(Seq(_))(_.head)
      .out(sendSessionToClientAsHeader)
      .mapOut(Seq(_))(_.head)
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(oneOffHeaderSessionLogic(inputs.head, required) match {
          case Left(l)  => Left(l)
          case Right(r) => Right(Seq(r._1), r._2)
        })
      }

  private[session] def oneOffCookieOrHeaderSessionLogic(
    maybeCookie: Option[String],
    maybeHeader: Option[String],
    required: Option[Boolean]
  ): Either[Unit, ((Option[CookieValueWithMeta], Option[String]), SessionResult[T])] = {
    maybeCookie match {
      case Some(cookie) =>
        val decoded = manager.clientSessionManager.decode(cookie)
        decoded match {
          case SessionResult.Expired | SessionResult.Corrupt(_) =>
            Right((None, maybeHeader), decoded)
          case s =>
            Right(
              (
                Some(manager.clientSessionManager.createCookieWithValue(cookie).valueWithMeta),
                maybeHeader
              ),
              s
            )
        }
      case _ =>
        maybeHeader match {
          case Some(header) =>
            val decoded = manager.clientSessionManager.decode(header)
            decoded match {
              case SessionResult.Expired | SessionResult.Corrupt(_) =>
                if (required.getOrElse(false))
                  Left(())
                else
                  Right((None, None), decoded)
              case s => Right((None, Some(header)), s)
            }
          case _ =>
            if (required.getOrElse(false))
              Left(())
            else
              Right((None, None), SessionResult.NoSession)
        }
    }
  }

  def oneOffCookieOrHeaderSession(
    required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[(Option[String], Option[String]), SessionResult[
    T
  ], Unit, Unit, (Option[CookieValueWithMeta], Option[String]), Unit, Any, Future] =
    endpoint
      .securityIn(getSessionFromClientAsCookie)
      .securityIn(getSessionFromClientAsHeader)
      .out(sendSessionToClientAsCookie)
      .out(sendSessionToClientAsHeader)
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { case (cookie, header) =>
        Future.successful(oneOffCookieOrHeaderSessionLogic(cookie, header, required))
      }

  private[session] def invalidateOneOffSessionLogic[SECURITY_OUTPUT, PRINCIPAL](
    result: (SECURITY_OUTPUT, PRINCIPAL),
    maybeCookie: Option[String],
    maybeHeader: Option[String]
  ): Either[Unit, ((Option[CookieValueWithMeta], Option[String]), PRINCIPAL)] = {
    val principal = result._2
    maybeCookie match {
      case Some(_) =>
        maybeHeader match {
          case Some(_) =>
            Right(
              (
                Some(
                  manager.clientSessionManager
                    .createCookieWithValue("deleted")
                    .withExpires(DateTime.MinValue)
                    .valueWithMeta
                ),
                Some("")
              ),
              principal
            )
          case _ =>
            Right(
              (
                Some(
                  manager.clientSessionManager
                    .createCookieWithValue("deleted")
                    .withExpires(DateTime.MinValue)
                    .valueWithMeta
                ),
                None
              ),
              principal
            )
        }
      case _ =>
        maybeHeader match {
          case Some(_) => Right((None, Some("")), principal)
          case _       => Right((None, None), principal)
        }
    }
  }

  def invalidateOneOffSession[
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
    (SECURITY_INPUT, Option[String], Option[String]),
    PRINCIPAL,
    Unit,
    Unit,
    (Option[CookieValueWithMeta], Option[String]),
    Unit,
    Any,
    Future
  ] =
    partial.endpoint
      .securityIn(getSessionFromClientAsCookie)
      .securityIn(getSessionFromClientAsHeader)
      .out(sendSessionToClientAsCookie)
      .out(sendSessionToClientAsHeader)
      .serverSecurityLogicWithOutput { case (si, cookie, header) =>
        partial.securityLogic(new FutureMonad())(si).map {
          case Left(l)  => Left(l)
          case Right(r) => invalidateOneOffSessionLogic(r, cookie, header)
        }
      }
}
