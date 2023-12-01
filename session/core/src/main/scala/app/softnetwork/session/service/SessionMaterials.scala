package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionManagers}
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

trait SessionMaterials[T <: SessionData] {

  implicit def manager(implicit
    sessionConfig: SessionConfig,
    companion: SessionDataCompanion[T]
  ): SessionManager[T]

  implicit def ts: ActorSystem[_]

  implicit def ec: ExecutionContext = ts.executionContext

  implicit def refreshTokenStorage: RefreshTokenStorage[T]

  protected def sessionType: Session.SessionType

  def headerAndForm: Boolean = false

}

trait BasicSessionMaterials[T <: SessionData] extends SessionMaterials[T] {

  override implicit def manager(implicit
    sessionConfig: SessionConfig,
    companion: SessionDataCompanion[T]
  ): SessionManager[T] =
    SessionManagers.basic
}

trait JwtSessionMaterials[T <: SessionData] extends SessionMaterials[T] {

  override implicit def manager(implicit
    sessionConfig: SessionConfig,
    companion: SessionDataCompanion[T]
  ): SessionManager[T] =
    SessionManagers.jwt

}
