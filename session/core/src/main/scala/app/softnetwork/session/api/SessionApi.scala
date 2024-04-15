package app.softnetwork.session.api

import akka.actor.typed.ActorSystem
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.launch.SessionGuardian
import app.softnetwork.session.model.{SessionDataCompanion, SessionManagers}
import com.softwaremill.session.{RefreshTokenStorage, SessionManager}
import org.softnetwork.session.model.Session

trait SessionApi extends SessionDataApi[Session] { _: SessionGuardian =>

  override implicit def companion: SessionDataCompanion[Session] = Session

  override protected def manager: SessionManager[Session] = SessionManagers.basic

  override protected def refreshTokenStorage: ActorSystem[_] => RefreshTokenStorage[Session] =
    sys => SessionRefreshTokenDao(sys)

}
