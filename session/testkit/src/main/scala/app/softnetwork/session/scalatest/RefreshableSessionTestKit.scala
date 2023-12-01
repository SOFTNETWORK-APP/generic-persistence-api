package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.model.SessionData
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.SessionOptions.refreshable
import com.softwaremill.session.SessionResult
import org.scalatest.Suite

import scala.util.{Failure, Success}

trait RefreshableSessionTestKit[T <: SessionData] extends SessionTestKit[T] {
  _: Suite with ApiRoutes with SessionMaterials[T] =>

  override val refreshableSession: Boolean = true

  override def extractSession(value: Option[String]): Option[T] = {
    value match {
      case Some(value) =>
        refreshable.refreshTokenManager
          .sessionFromValue(value) complete () match {
          case Success(value) =>
            value match {
              case _ @SessionResult.CreatedFromToken(session) => Some(session)
              case _                                          => None
            }
          case Failure(_) => None
        }
      case _ => None
    }
  }
}
