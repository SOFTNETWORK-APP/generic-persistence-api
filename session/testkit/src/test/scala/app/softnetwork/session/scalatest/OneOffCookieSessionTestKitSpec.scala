package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class OneOffCookieSessionTestKitSpec
    extends SessionTestKitSpec
    with OneOffCookieSessionTestKit
    with CsrfCheckHeader
