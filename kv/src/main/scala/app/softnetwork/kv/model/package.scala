package app.softnetwork.kv

import app.softnetwork.persistence.model.State

package object model {

  trait KvState extends State {
    def key: String
    def value: String
    val uuid: String = key
  }

  trait KvCompanion {
    def apply(key: String, value: String): KvState
  }

}