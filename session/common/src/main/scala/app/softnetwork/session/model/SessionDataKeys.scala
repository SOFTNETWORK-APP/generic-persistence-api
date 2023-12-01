package app.softnetwork.session.model

import app.softnetwork.session.config.Settings.Session.DefaultSessionConfig

trait SessionDataKeys {
  val adminKey = "admin"

  val profileKey = "profile"

  val anonymousKey = "anonymous"

  lazy val idKey: String = DefaultSessionConfig.sessionCookieConfig.name

  val clientIdKey: String = "client_id"
}
