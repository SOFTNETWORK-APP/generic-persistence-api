package app.softnetwork.api.server

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.serialization._
import org.json4s.Formats

/** Simplest possible implementation of a health check More realistic implementation should include
  * actual checking of the service's internal state, verifying needed actors are still alive, and so
  * on.
  */
object HealthCheckService extends Directives with DefaultComplete {

  implicit def formats: Formats = commonFormats

  val route: Route = {
    path("healthcheck") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }
}
