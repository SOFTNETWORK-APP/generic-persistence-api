package app.softnetwork.session.scalatest

package Endpoints {

  import app.softnetwork.session.service.BasicSessionMaterials
  import app.softnetwork.session.model.SessionDataCompanion
  import app.softnetwork.session.handlers.SessionRefreshTokenDao
  import app.softnetwork.session.service.JwtClaimsSessionMaterials

  import com.softwaremill.session.RefreshTokenStorage
  import org.softnetwork.session.model.{JwtClaims, Session}

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
          extends SessionTestKitSpec[JwtClaims]
          with OneOffCookieSessionEndpointsTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
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
          extends SessionTestKitSpec[JwtClaims]
          with OneOffHeaderSessionEndpointsTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
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
          extends SessionTestKitSpec[JwtClaims]
          with RefreshableCookieSessionEndpointsTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
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
          extends SessionTestKitSpec[JwtClaims]
          with RefreshableHeaderSessionEndpointsTestKit[JwtClaims]
          with JwtClaimsSessionMaterials
          with ApiKeyLoader {
        override implicit def companion: SessionDataCompanion[JwtClaims] = JwtClaims
      }
    }

  }

}
