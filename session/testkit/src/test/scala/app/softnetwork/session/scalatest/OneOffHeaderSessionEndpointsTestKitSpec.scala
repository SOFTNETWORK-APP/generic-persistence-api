package app.softnetwork.session.scalatest

import app.softnetwork.session.CsrfCheckHeader

class OneOffHeaderSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with OneOffHeaderSessionEndpointsTestKit
    with CsrfCheckHeader
