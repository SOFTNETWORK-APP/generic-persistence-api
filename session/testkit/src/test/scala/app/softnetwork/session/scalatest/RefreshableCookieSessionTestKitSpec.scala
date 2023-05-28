package app.softnetwork.session.scalatest

import com.softwaremill.session.CsrfCheckHeader

class RefreshableCookieSessionTestKitSpec
    extends SessionTestKitSpec
    with RefreshableCookieSessionTestKit
    with CsrfCheckHeader
