package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class RefreshableHeaderSessionTestKitSpec
    extends SessionTestKitSpec
    with RefreshableHeaderSessionTestKit
    with CsrfCheckHeader
