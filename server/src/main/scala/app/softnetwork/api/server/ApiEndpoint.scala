package app.softnetwork.api.server

import akka.http.scaladsl.server.Route
import app.softnetwork.serialization.commonFormats
import org.json4s.Formats

import scala.language.implicitConversions

trait ApiEndpoint extends Endpoint {

  import Endpoint._

  final def apiRoute: Route = endpoints

  override def route: Route = apiRoute

  implicit def formats: Formats = commonFormats

}
