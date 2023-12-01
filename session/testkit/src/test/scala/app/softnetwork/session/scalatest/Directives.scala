package app.softnetwork.session.scalatest

import app.softnetwork.session.service.{BasicSessionMaterials, JwtSessionMaterials}

package Directives {

  import app.softnetwork.session.model.SessionDataCompanion
  import app.softnetwork.session.handlers.SessionRefreshTokenDao
  import com.softwaremill.session.RefreshTokenStorage
  import org.softnetwork.session.model.Session

  package OneOff {
    package Cookie {
      class OneOffCookieBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffCookieSessionServiceTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session
        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }

      class OneOffCookieJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffCookieSessionServiceTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)
      }
    }

    package Header {

      class OneOffHeaderBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffHeaderSessionServiceTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)
      }

      class OneOffHeaderJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with OneOffHeaderSessionServiceTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)
      }
    }
  }

  package Refreshable {
    package Cookie {
      class RefreshableCookieBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableCookieSessionServiceTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)
      }

      class RefreshableCookieJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableCookieSessionServiceTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)
      }
    }

    package Header {

      class RefreshableHeaderBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableHeaderSessionServiceTestKit[Session]
          with BasicSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }

      class RefreshableHeaderJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec[Session]
          with RefreshableHeaderSessionServiceTestKit[Session]
          with JwtSessionMaterials[Session] {
        implicit def companion: SessionDataCompanion[Session] = Session

        implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(ts)

      }
    }

  }
}
