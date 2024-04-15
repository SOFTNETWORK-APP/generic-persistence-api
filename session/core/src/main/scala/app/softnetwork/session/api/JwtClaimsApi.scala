package app.softnetwork.session.api

import akka.actor.typed.ActorSystem
import app.softnetwork.session.handlers.JwtClaimsRefreshTokenDao
import app.softnetwork.session.launch.SessionGuardian
import app.softnetwork.session.model.{SessionDataCompanion, SessionManagers}
import com.softwaremill.session.{RefreshTokenStorage, SessionManager}
import org.softnetwork.session.model.JwtClaims

trait JwtClaimsApi extends SessionDataApi[JwtClaims] { _: SessionGuardian =>

  override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims

  override protected def manager: SessionManager[JwtClaims] = SessionManagers.jwt

  override protected def refreshTokenStorage: ActorSystem[_] => RefreshTokenStorage[JwtClaims] =
    sys => JwtClaimsRefreshTokenDao(sys)

}
