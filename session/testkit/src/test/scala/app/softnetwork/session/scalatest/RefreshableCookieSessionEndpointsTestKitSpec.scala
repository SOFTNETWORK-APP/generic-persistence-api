package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class RefreshableCookieSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with RefreshableCookieSessionEndpointsTestKit
    with CsrfCheckHeader
