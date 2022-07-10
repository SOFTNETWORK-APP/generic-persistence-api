package app.softnetwork.persistence.auth.persistence.typed

import app.softnetwork.kv.persistence.typed.KeyValueBehavior
import app.softnetwork.persistence.auth.config.Settings

trait AccountKeyBehavior extends KeyValueBehavior{

  override def persistenceId: String = "AccountKeys"

  /**
    *
    * @return node role required to start this actor
    */
  override lazy val role: String = Settings.AkkaNodeRole
}

object AccountKeyBehavior extends AccountKeyBehavior
