package app.softnetwork.session.scalatest

import app.softnetwork.session.service.{BasicSessionMaterials, JwtSessionMaterials}

package Endpoints {
  package OneOff {
    package Cookie {
      class OneOffCookieBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with OneOffCookieSessionEndpointsTestKit
          with BasicSessionMaterials

      class OneOffCookieJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with OneOffCookieSessionEndpointsTestKit
          with JwtSessionMaterials
    }

    package Header {

      class OneOffHeaderBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with OneOffHeaderSessionEndpointsTestKit
          with BasicSessionMaterials

      class OneOffHeaderJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with OneOffHeaderSessionEndpointsTestKit
          with JwtSessionMaterials
    }

  }

  package Refreshable {
    package Cookie {
      class RefreshableCookieBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with RefreshableCookieSessionEndpointsTestKit
          with BasicSessionMaterials

      class RefreshableCookieJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with RefreshableCookieSessionEndpointsTestKit
          with JwtSessionMaterials
    }

    package Header {

      class RefreshableHeaderBasicSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with RefreshableHeaderSessionEndpointsTestKit
          with BasicSessionMaterials

      class RefreshableHeaderJwtSessionEndpointsTestKitSpec
          extends SessionTestKitSpec
          with RefreshableHeaderSessionEndpointsTestKit
          with JwtSessionMaterials
    }

  }

}
