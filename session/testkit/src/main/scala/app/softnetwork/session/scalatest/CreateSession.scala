package app.softnetwork.session.scalatest

case class CreateSession(id: String, profile: Option[String] = None, admin: Option[Boolean] = None)
