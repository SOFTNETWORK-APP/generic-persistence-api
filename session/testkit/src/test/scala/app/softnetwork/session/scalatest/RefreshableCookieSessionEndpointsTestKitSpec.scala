package app.softnetwork.session.scalatest

import app.softnetwork.session.CsrfCheckHeader

class RefreshableCookieSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with RefreshableCookieSessionEndpointsTestKit
    with CsrfCheckHeader
