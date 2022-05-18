package app.softnetwork.kv

import app.softnetwork.persistence.model.State

package object model {

  @SerialVersionUID(0L)
  case class KeyValue(key: String, value: String) extends State {
    val uuid: String = key
  }

}
