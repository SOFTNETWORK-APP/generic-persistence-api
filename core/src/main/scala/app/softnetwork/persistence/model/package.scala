package app.softnetwork.persistence

import java.time.Instant

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
}
