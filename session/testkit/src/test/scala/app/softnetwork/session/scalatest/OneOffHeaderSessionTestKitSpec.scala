package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class OneOffHeaderSessionTestKitSpec
    extends SessionTestKitSpec
    with OneOffHeaderSessionTestKit
    with CsrfCheckHeader
