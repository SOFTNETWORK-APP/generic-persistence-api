package app.softnetwork.session.scalatest

package Directives {

  import app.softnetwork.session.service.BasicSessionMaterials
  import app.softnetwork.session.model.SessionDataCompanion
  import app.softnetwork.session.handlers.SessionRefreshTokenDao
  import app.softnetwork.session.service.JwtClaimsSessionMaterials

  import com.softwaremill.session.RefreshTokenStorage
  import org.softnetwork.session.model.{JwtClaims, Session}

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
          extends SessionTestKitSpec[JwtClaims]
          with OneOffCookieSessionServiceTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
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
          extends SessionTestKitSpec[JwtClaims]
          with OneOffHeaderSessionServiceTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
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
          extends SessionTestKitSpec[JwtClaims]
          with RefreshableCookieSessionServiceTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
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
          extends SessionTestKitSpec[JwtClaims]
          with RefreshableHeaderSessionServiceTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
      }
    }

  }
}
