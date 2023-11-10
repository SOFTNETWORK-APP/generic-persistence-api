package app.softnetwork.api.server

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import app.softnetwork.serialization.commonFormats
import org.json4s.Formats
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.model.{HeaderNames, StatusCode, Uri}
import sttp.tapir.EndpointOutput.OneOf
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir._
import sttp.tapir.generic.auto.SchemaDerivation
import sttp.tapir.json.json4s.TapirJson4s

import scala.concurrent.Future
import scala.language.implicitConversions

object ApiErrors extends SchemaDerivation with TapirJson4s {

  sealed trait ErrorInfo {
    val message: String
  }

  trait ExtendedErrorInfo extends ErrorInfo

  /** Represents http 404. */
  case class NotFound(message: String) extends ErrorInfo

  /** Represents http 401. */
  case class Unauthorized(message: String) extends ErrorInfo

  /** Represents http 403. */
  case class Forbidden(message: String) extends ErrorInfo

  /** Represents http 409. */
  case class Conflict(message: String) extends ErrorInfo

  /** Default case. */
  case class ErrorMessage(message: String) extends ErrorInfo

  /** Represents http 400. */
  case class BadRequest(message: String) extends ErrorInfo

  /** Represents http 500. */
  case class InternalServerError(message: String) extends ErrorInfo

  /** Represents http 302. */
  case class Found(uri: Uri) extends ErrorInfo {
    override val message: String = s"Found $uri"
    override def toString: String = uri.toString()
  }

  object Found {
    def apply(uri: String): Option[Found] = Uri.parse(uri).toOption.map(Found(_))
  }

  implicit val foundCodec: Codec[String, Found, CodecFormat.TextPlain] =
    Codec.string.mapDecode(s =>
      Uri.parse(s) match {
        case Right(r) => DecodeResult.Value(Found(r))
        case Left(l)  => DecodeResult.Error(s, new IllegalArgumentException(l))
      }
    )(_.toString())

  implicit def leftFoundCodec[T]: Codec[String, Left[Found, T], CodecFormat.TextPlain] =
    Codec.string.mapDecode(s =>
      Uri.parse(s) match {
        case Right(r) => DecodeResult.Value(Left[Found, T](Found(r)))
        case Left(l)  => DecodeResult.Error(s, new IllegalArgumentException(l))
      }
    )(_.left.get.toString())

  case class UnauthorizedWithChallenge(scheme: String, realm: String) extends ErrorInfo {
    override val message: String = "Unauthorized"
    override def toString: String = WWWAuthenticateChallenge(scheme).realm(realm).toString()
  }

  implicit val unauthorizedWithChallengeCodec
    : Codec[String, UnauthorizedWithChallenge, CodecFormat.TextPlain] =
    Codec.string.mapDecode(s =>
      WWWAuthenticateChallenge.parseSingle(s) match {
        case Right(challenge) =>
          DecodeResult.Value(
            UnauthorizedWithChallenge(challenge.scheme, challenge.realm.getOrElse(""))
          )
        case Left(_) => DecodeResult.Error(s, new Exception("Cannot parse WWW-Authenticate header"))
      }
    )(_.toString())

  implicit def apiError2Route(apiError: ErrorInfo)(implicit formats: Formats): Route =
    apiError match {
      case r: BadRequest => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
      case r: Conflict   => complete(HttpResponse(StatusCodes.Conflict, entity = r))
      case r: Forbidden  => complete(HttpResponse(StatusCodes.Forbidden, entity = r))
      case r: InternalServerError =>
        complete(HttpResponse(StatusCodes.InternalServerError, entity = r))
      case r: NotFound     => complete(HttpResponse(StatusCodes.NotFound, entity = r))
      case r: Unauthorized => complete(HttpResponse(StatusCodes.Unauthorized, entity = r))
      case r: Found        => redirect(r.toString, StatusCodes.Found)
      case r: ErrorMessage => complete(HttpResponse(StatusCodes.OK, entity = r))
    }

  implicit def formats: Formats = commonFormats

  import app.softnetwork.serialization._

  val forbiddenVariant: EndpointOutput.OneOfVariant[ApiErrors.Forbidden] =
    oneOfVariant(
      statusCode(StatusCode.Forbidden).and(
        jsonBody[ApiErrors.Forbidden]
          .description("When user doesn't have role for the endpoint")
      )
    )

  val unauthorizedVariant: EndpointOutput.OneOfVariant[ApiErrors.Unauthorized] =
    oneOfVariant(
      statusCode(StatusCode.Unauthorized).and(
        jsonBody[ApiErrors.Unauthorized]
          .description("When user doesn't authenticated or token is expired")
      )
    )

  val notFoundVariant: EndpointOutput.OneOfVariant[ApiErrors.NotFound] =
    oneOfVariant(
      statusCode(StatusCode.NotFound)
        .and(jsonBody[ApiErrors.NotFound].description("When something not found"))
    )

  val foundVariant: EndpointOutput.OneOfVariant[ApiErrors.Found] =
    oneOfVariant(
      statusCode(StatusCode.Found)
        .and(header[ApiErrors.Found](HeaderNames.Location).description("Redirect address"))
    )

  val badRequestVariant: EndpointOutput.OneOfVariant[ApiErrors.BadRequest] =
    oneOfVariant(
      statusCode(StatusCode.BadRequest)
        .and(jsonBody[ApiErrors.BadRequest].description("Bad request"))
    )

  val internalServerErrorVariant: EndpointOutput.OneOfVariant[ApiErrors.InternalServerError] =
    oneOfVariant(
      statusCode(StatusCode.InternalServerError)
        .and(jsonBody[ApiErrors.InternalServerError].description("For exceptional cases"))
    )

  val defaultErrorVariant: EndpointOutput.OneOfVariant[ApiErrors.ErrorMessage] =
    oneOfDefaultVariant(
      jsonBody[ApiErrors.ErrorMessage]
        .description("Default result")
        .example(ApiErrors.ErrorMessage("Test error message"))
    )

  val unauthorizedWithChallengeVariant: EndpointOutput.OneOfVariant[UnauthorizedWithChallenge] =
    oneOfVariant(
      statusCode(StatusCode.Unauthorized)
        .and(header[UnauthorizedWithChallenge](HeaderNames.WwwAuthenticate))
    )

  val oneOfApiErrors: EndpointOutput.OneOf[ApiErrors.ErrorInfo, ApiErrors.ErrorInfo] =
    oneOf[ApiErrors.ErrorInfo](
      // returns required http code for different types of ErrorInfo.
      // For secured endpoint you need to define
      // all cases before defining security logic
      forbiddenVariant,
      unauthorizedVariant,
      unauthorizedWithChallengeVariant,
      notFoundVariant,
      foundVariant,
      badRequestVariant,
      internalServerErrorVariant,
      // default case below.
      defaultErrorVariant
    )

  def oneOfEitherApiErrors[T: Manifest: Schema]()
    : OneOf[Either[ApiErrors.ErrorInfo, T], Either[ApiErrors.ErrorInfo, T]] = {
    oneOf[Either[ApiErrors.ErrorInfo, T]](
      oneOfVariantValueMatcher(StatusCode.Ok, jsonBody[Right[ApiErrors.ErrorInfo, T]]) {
        case Right(_) => true
      },
      oneOfVariantValueMatcher(
        StatusCode.Forbidden,
        jsonBody[Left[ApiErrors.Forbidden, T]]
          .description("When user doesn't have role for the endpoint")
      ) { case Left(ApiErrors.Forbidden(_)) =>
        true
      },
      oneOfVariantValueMatcher(
        StatusCode.Unauthorized,
        jsonBody[Left[ApiErrors.Unauthorized, T]]
          .description("When user doesn't authenticated or token is expired")
      ) { case Left(ApiErrors.Unauthorized(_)) =>
        true
      },
      oneOfVariantValueMatcher(
        StatusCode.NotFound,
        jsonBody[Left[ApiErrors.NotFound, T]].description("When something not found")
      ) { case Left(ApiErrors.NotFound(_)) =>
        true
      },
      oneOfVariantValueMatcher(
        StatusCode.Found,
        header[Left[ApiErrors.Found, T]]("Location").description("Redirect address")
      ) { case Left(ApiErrors.Found(_)) =>
        true
      },
      oneOfVariantValueMatcher(
        StatusCode.BadRequest,
        jsonBody[Left[ApiErrors.BadRequest, T]].description("Bad request")
      ) { case Left(ApiErrors.BadRequest(_)) =>
        true
      },
      oneOfVariantValueMatcher(
        StatusCode.InternalServerError,
        jsonBody[Left[ApiErrors.InternalServerError, T]].description("For exceptional cases")
      ) { case Left(ApiErrors.InternalServerError(_)) =>
        true
      },
      oneOfDefaultVariant(
        jsonBody[Left[ApiErrors.ErrorMessage, T]]
          .description("Default result")
          .and(statusCode(StatusCode.BadRequest))
      )
    )

  }

  def withApiErrorVariants[SECURITY_INPUT, PRINCIPAL, INPUT, SECURITY_OUTPUT, OUTPUT](
    body: => PartialServerEndpointWithSecurityOutput[
      SECURITY_INPUT,
      PRINCIPAL,
      INPUT,
      Unit,
      SECURITY_OUTPUT,
      OUTPUT,
      Any,
      Future
    ]
  ): PartialServerEndpointWithSecurityOutput[
    SECURITY_INPUT,
    PRINCIPAL,
    INPUT,
    Any,
    SECURITY_OUTPUT,
    OUTPUT,
    Any,
    Future
  ] =
    body.errorOutVariants(
      forbiddenVariant,
      unauthorizedVariant,
      unauthorizedWithChallengeVariant,
      notFoundVariant,
      foundVariant,
      badRequestVariant,
      internalServerErrorVariant,
      defaultErrorVariant
    )

}
