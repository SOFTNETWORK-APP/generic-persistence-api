package app.softnetwork.persistence

import java.util.Date

/** Created by smanciot on 27/05/2020.
  */
package object model {

  trait Entity {
    final val ALL_KEY = "*"
  }

  /**
    * The in-memory state of the entity actor
    */
  trait State {
    def uuid: String
  }

  trait Timestamped extends State {
    def lastUpdated: Date
    def createdDate: Date
  }

  /**
    * Marker trait for serialization with Protobuf
    */
  trait ProtobufDomainObject

  /**
    * Marker trait for serialization with Jackson CBOR
    */
  trait CborDomainObject

  trait ProtobufStateObject extends ProtobufDomainObject with State
}
