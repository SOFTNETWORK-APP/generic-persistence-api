package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.SessionData
import app.softnetwork.session.service.SessionMaterials
import org.scalatest.Suite

trait OneOffSessionTestKit[T <: SessionData] extends SessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override val refreshableSession: Boolean = false

  override def extractSession(value: Option[String]): Option[T] =
    value match {
      case Some(value) => manager.clientSessionManager.decode(value).toOption
      case _           => None
    }

}
