package app.softnetwork.session.model

import app.softnetwork.persistence.generateUUID
import app.softnetwork.session.config.Settings.Session.Continuity

trait SessionDataCompanion[T <: SessionData] extends SessionDataKeys {

  val _refreshable: Boolean = Continuity match {
    case "refreshable" => true
    case _             => false
  }

  def newSession: T with SessionDataDecorator[T]

  def apply(): T with SessionDataDecorator[T] =
    newSession.withId(generateUUID()).withRefreshable(_refreshable)

  def apply(data: String): T with SessionDataDecorator[T] =
    newSession.withId(data).withRefreshable(_refreshable)

}
