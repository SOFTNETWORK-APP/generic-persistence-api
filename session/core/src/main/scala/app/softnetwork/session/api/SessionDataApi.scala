package app.softnetwork.session.api

import akka.actor.typed.ActorSystem
import app.softnetwork.session.config.Settings
import app.softnetwork.session.launch.SessionGuardian
import app.softnetwork.session.model.{SessionData, SessionDataCompanion, SessionDataDecorator}
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionManager}
import org.softnetwork.session.model.Session

trait SessionDataApi[SD <: SessionData with SessionDataDecorator[SD]] { _: SessionGuardian =>

  implicit def sessionConfig: SessionConfig = Settings.Session.DefaultSessionConfig

  implicit def companion: SessionDataCompanion[SD]

  override protected def sessionType: Session.SessionType =
    Settings.Session.SessionContinuityAndTransport

  protected def manager: SessionManager[SD]

  protected def refreshTokenStorage: ActorSystem[_] => RefreshTokenStorage[SD]

}
