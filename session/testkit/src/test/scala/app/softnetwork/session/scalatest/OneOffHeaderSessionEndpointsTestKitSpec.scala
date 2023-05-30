package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class OneOffHeaderSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with OneOffHeaderSessionEndpointsTestKit
    with CsrfCheckHeader
