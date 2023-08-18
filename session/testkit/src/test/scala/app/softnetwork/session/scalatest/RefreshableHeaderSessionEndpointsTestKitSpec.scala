package app.softnetwork.session.scalatest

import app.softnetwork.session.CsrfCheckHeader

class RefreshableHeaderSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with RefreshableHeaderSessionEndpointsTestKit
    with CsrfCheckHeader
