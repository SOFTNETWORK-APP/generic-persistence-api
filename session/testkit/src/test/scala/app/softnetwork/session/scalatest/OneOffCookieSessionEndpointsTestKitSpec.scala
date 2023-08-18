package app.softnetwork.session.scalatest

import app.softnetwork.session.CsrfCheckHeader

class OneOffCookieSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with OneOffCookieSessionEndpointsTestKit
    with CsrfCheckHeader
