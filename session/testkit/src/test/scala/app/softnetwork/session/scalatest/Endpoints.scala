package app.softnetwork.session.scalatest

import app.softnetwork.session.service.{BasicSessionMaterials, JwtSessionMaterials}
import app.softnetwork.session.model.SessionDataCompanion
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import com.softwaremill.session.RefreshTokenStorage
import org.softnetwork.session.model.Session

package Endpoints {
  package OneOff {
    package Cookie {
      class OneOffCookieBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffCookieSessionEndpointsTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }

      class OneOffCookieJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffCookieSessionEndpointsTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }
    }

    package Header {

      class OneOffHeaderBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffHeaderSessionEndpointsTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }

      class OneOffHeaderJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffHeaderSessionEndpointsTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }
    }

  }

  package Refreshable {
    package Cookie {
      class RefreshableCookieBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableCookieSessionEndpointsTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }

      class RefreshableCookieJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableCookieSessionEndpointsTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }
    }

    package Header {

      class RefreshableHeaderBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableHeaderSessionEndpointsTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }

      class RefreshableHeaderJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableHeaderSessionEndpointsTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }
    }

  }

}
