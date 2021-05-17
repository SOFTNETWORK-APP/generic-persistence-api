package app.softnetwork.persistence

import java.util.Date

/**
  * Created by smanciot on 27/05/2020.
  */
package object model {

  trait Entity {
    final val ALL_KEY = "*"
  }

  /** State objects **/
  trait State{
    def uuid: String
  }

  trait Timestamped extends State {
    def lastUpdated: Date
    def createdDate: Date
  }

  trait ProtobufDomainObject

  trait CborDomainObject

  trait ProtobufStateObject extends ProtobufDomainObject with State
}
