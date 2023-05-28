package app.softnetwork.session.scalatest

import com.softwaremill.session.CookieConfig
import org.scalatest.Suite

trait CookieSessionTestKit extends SessionTestKit {
  _: Suite =>

  def cookieConfig: CookieConfig

  final override val sessionHeaderName: String = cookieConfig.name

}
