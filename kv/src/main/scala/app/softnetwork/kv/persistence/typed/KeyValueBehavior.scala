package app.softnetwork.kv.persistence.typed

import app.softnetwork.kv.model.KeyValue

trait KeyValueBehavior extends KvBehavior[KeyValue]{

  override def persistenceId: String = "KeyValue"

  override def createKv(key: String, value: String): KeyValue = KeyValue(key, value)
}

object KeyValueBehavior extends KeyValueBehavior
