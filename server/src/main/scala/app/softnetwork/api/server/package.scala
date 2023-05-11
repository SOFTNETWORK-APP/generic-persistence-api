package app.softnetwork.api

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, ResponseEntity}
import app.softnetwork.persistence.message.ErrorMessage
import app.softnetwork.serialization._

import org.json4s.Formats

import scala.language.implicitConversions

/** Created by smanciot on 03/06/2020.
  */
package object server {

  implicit def entityAsJson[T <: AnyRef: Manifest](
    entity: T
  )(implicit formats: Formats): ResponseEntity = {
    entity match {
      case error: ErrorMessage =>
        HttpEntity(`application/json`, serialization.write(Map("message" -> error.message)))
      case _ => HttpEntity(`application/json`, serialization.write(entity))
    }
  }

}
