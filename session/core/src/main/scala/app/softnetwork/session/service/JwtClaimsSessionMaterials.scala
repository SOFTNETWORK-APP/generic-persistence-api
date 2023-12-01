package app.softnetwork.session.service

import app.softnetwork.session.handlers.JwtClaimsRefreshTokenDao
import app.softnetwork.session.model.{JwtClaimsEncoder, SessionDataCompanion}
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig, SessionEncoder, SessionManager}
import org.softnetwork.session.model.{ApiKey, JwtClaims}

import scala.concurrent.Future
import scala.language.reflectiveCalls

trait JwtClaimsSessionMaterials extends SessionMaterials[JwtClaims] {
  self: {
    def loadApiKey(clientId: String): Future[Option[ApiKey]]
  } =>

  implicit def refreshTokenStorage: RefreshTokenStorage[JwtClaims] = JwtClaimsRefreshTokenDao(ts)

  override def manager(implicit
    sessionConfig: SessionConfig,
    companion: SessionDataCompanion[JwtClaims]
  ): SessionManager[JwtClaims] = {
    implicit val encoder: SessionEncoder[JwtClaims] = new JwtClaimsEncoder {
      override def loadApiKey(clientId: String): Future[Option[ApiKey]] = self.loadApiKey(clientId)
    }
    new SessionManager[JwtClaims](sessionConfig)
  }

}
