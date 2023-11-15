package app.softnetwork.session.scalatest

import app.softnetwork.session.service.{BasicSessionMaterials, JwtSessionMaterials}

package Directives {
  package OneOff {
    package Cookie {
      class OneOffCookieBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with OneOffCookieSessionServiceTestKit
          with BasicSessionMaterials

      class OneOffCookieJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with OneOffCookieSessionServiceTestKit
          with JwtSessionMaterials
    }

    package Header {

      class OneOffHeaderBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with OneOffHeaderSessionServiceTestKit
          with BasicSessionMaterials

      class OneOffHeaderJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with OneOffHeaderSessionServiceTestKit
          with JwtSessionMaterials
    }
  }

  package Refreshable {
    package Cookie {
      class RefreshableCookieBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with RefreshableCookieSessionServiceTestKit
          with BasicSessionMaterials

      class RefreshableCookieJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with RefreshableCookieSessionServiceTestKit
          with JwtSessionMaterials
    }

    package Header {

      class RefreshableHeaderBasicSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with RefreshableHeaderSessionServiceTestKit
          with BasicSessionMaterials

      class RefreshableHeaderJwtSessionServiceTestKitSpec
          extends SessionTestKitSpec
          with RefreshableHeaderSessionServiceTestKit
          with JwtSessionMaterials
    }

  }
}
