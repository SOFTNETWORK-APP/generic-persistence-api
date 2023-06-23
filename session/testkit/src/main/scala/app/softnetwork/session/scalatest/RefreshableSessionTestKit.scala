package app.softnetwork.session.scalatest

import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import com.softwaremill.session.SessionOptions.refreshable
import com.softwaremill.session.{RefreshTokenStorage, SessionResult}
import org.scalatest.Suite
import org.softnetwork.session.model.Session

import scala.util.{Failure, Success}

trait RefreshableSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes =>

  override val refreshableSession: Boolean = true

  implicit lazy val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    typedSystem()
  )

  override def extractSession(value: Option[String]): Option[Session] = {
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