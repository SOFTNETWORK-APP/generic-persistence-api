package app.softnetwork.api.server.client

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{AkkaGrpcClient, SingleResponseRequestBuilder}

import java.net.PasswordAuthentication
import scala.concurrent.ExecutionContext

trait GrpcClient {

  implicit def system: ActorSystem[_]
  implicit lazy val ec: ExecutionContext = system.executionContext

  def name: String

  def grpcClient: AkkaGrpcClient

  /** add headers to grpc request
    * @param request
    *   - grpc request
    * @param headers
    *   - headers
    * @tparam Req
    *   - Request
    * @tparam Res
    *   - Response
    * @return
    *   request with headers
    */
  def withHeaders[Req, Res](
    request: SingleResponseRequestBuilder[Req, Res],
    headers: Seq[(String, String)]
  ): SingleResponseRequestBuilder[Req, Res] = {
    headers.foldLeft(request) { case (acc, (k, v)) =>
      acc.addHeader(k, v)
    }
  }

  /** add authorization header to grpc request
    * @param request
    *   - grpc request
    * @param value
    *   - authorization value
    * @tparam Req
    *   - Request
    * @tparam Res
    *   - Response
    * @return
    *   request with authorization header
    */
  def withAuthorization[Req, Res](
    request: SingleResponseRequestBuilder[Req, Res],
    value: String
  ): SingleResponseRequestBuilder[Req, Res] = {
    withHeaders(request, Seq("Authorization" -> value))
  }

  /** add oauth2 authorization header to grpc request
    * @param request
    *   - grpc request
    * @param token
    *   - oauth2 token
    * @tparam Req
    *   - Request
    * @tparam Res
    *   - Response
    * @return
    *   request with oauth2 authorization
    */
  def oauth2[Req, Res](
    request: SingleResponseRequestBuilder[Req, Res],
    token: String
  ): SingleResponseRequestBuilder[Req, Res] = {
    withAuthorization(request, s"Bearer $token")
  }

  /** add basic authorization header to grpc request
    * @param request
    *   - grpc request
    * @param credentials
    *   - basic credentials
    * @tparam Req
    *   - Request
    * @tparam Res
    *   - Response
    * @return
    *   request with basic authorization
    */
  def basic[Req, Res](
    request: SingleResponseRequestBuilder[Req, Res],
    credentials: PasswordAuthentication
  ): SingleResponseRequestBuilder[Req, Res] = {
    val token = java.util.Base64.getEncoder.encodeToString(
      s"${credentials.getUserName}:${new String(credentials.getPassword)}".getBytes
    )
    withAuthorization(request, s"Basic $token")
  }
}
