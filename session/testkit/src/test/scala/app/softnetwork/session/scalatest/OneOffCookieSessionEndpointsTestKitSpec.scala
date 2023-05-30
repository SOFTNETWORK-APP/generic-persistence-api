package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class OneOffCookieSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with OneOffCookieSessionEndpointsTestKit
    with CsrfCheckHeader
