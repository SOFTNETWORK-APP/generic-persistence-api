package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class RefreshableHeaderSessionEndpointsTestKitSpec
    extends SessionTestKitSpec
    with RefreshableHeaderSessionEndpointsTestKit
    with CsrfCheckHeader
