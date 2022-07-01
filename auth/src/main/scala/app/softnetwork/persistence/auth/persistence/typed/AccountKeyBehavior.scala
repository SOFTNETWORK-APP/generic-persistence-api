package app.softnetwork.persistence.auth.persistence.typed

import app.softnetwork.kv.persistence.typed.KeyValueBehavior

trait AccountKeyBehavior extends KeyValueBehavior{

  override def persistenceId: String = "AccountKeys"

}

object AccountKeyBehavior extends AccountKeyBehavior
