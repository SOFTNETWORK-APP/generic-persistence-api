package app.softnetwork.persistence

import app.softnetwork.serialization._
import org.json4s.Formats

import java.time.Instant
import scala.language.postfixOps
import scala.reflect.ClassTag

/** Created by smanciot on 27/05/2020.
  */
package object model {

  trait Entity {
    final val ALL_KEY = "*"
  }

  /** The in-memory state of the entity actor
    */
  trait State {
    def uuid: String
  }

  trait Timestamped extends State {
    def lastUpdated: Instant
    def createdDate: Instant
  }

  /** Marker trait for serializing a Domain Object using Protobuf Serializer
    */
  trait ProtobufDomainObject

  /** Marker trait for serializing a Domain Object using Jackson CBOR Serializer
    */
  trait CborDomainObject

  trait ProtobufStateObject extends ProtobufDomainObject with State

  implicit class CamelCaseString(s: String) {
    def toSnakeCase: String = s.foldLeft("") { (acc, char) =>
      if (char.isUpper) {
        if (acc.isEmpty) char.toLower.toString
        else acc + "_" + char.toLower
      } else {
        acc + char
      }
    }
    def $: String = toSnakeCase
  }

  case class StateWrapper[T <: State](
    uuid: String,
    lastUpdated: Instant,
    deleted: Boolean,
    state: Option[T]
  ) extends State {
    def asJson(implicit formats: Formats): String = {
      serialization.write[StateWrapper[T]](this.copy(deleted = deleted || state.isEmpty))
    }
  }

  trait StateWrappertReader[T <: State] extends ManifestWrapper[StateWrapper[T]] {
    def read(json: String)(implicit formats: Formats): StateWrapper[T] = {
      serialization.read[StateWrapper[T]](json)(formats, manifestWrapper.wrapped)
    }
  }
}
