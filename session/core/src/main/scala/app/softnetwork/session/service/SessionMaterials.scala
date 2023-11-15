package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import app.softnetwork.session.config.Settings
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.model.SessionManagers
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.json4s.Formats
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

trait SessionMaterials {

  implicit def manager(implicit sessionConfig: SessionConfig): SessionManager[Session]

  implicit def ts: ActorSystem[_]

  implicit def ec: ExecutionContext = ts.executionContext

  implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

  protected def sessionType: Session.SessionType = Settings.Session.SessionContinuityAndTransport

  def headerAndForm: Boolean = false

}

trait BasicSessionMaterials extends SessionMaterials {

  override implicit def manager(implicit sessionConfig: SessionConfig): SessionManager[Session] =
    SessionManagers.basic
}

trait JwtSessionMaterials extends SessionMaterials { _: { def formats: Formats } =>

  override implicit def manager(implicit sessionConfig: SessionConfig): SessionManager[Session] =
    SessionManagers.jwt(sessionConfig, formats)

}
