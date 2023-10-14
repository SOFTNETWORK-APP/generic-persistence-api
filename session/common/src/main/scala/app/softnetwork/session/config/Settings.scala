package app.softnetwork.session.config

/** Created by smanciot on 21/03/2018.
  */
import com.typesafe.config.{Config, ConfigFactory}
import configs.ConfigError
import org.softnetwork.session.model.Session.SessionType

object Settings {
  lazy val config: Config = ConfigFactory.load()

  def configErrorsToException(err: ConfigError) =
    new IllegalStateException(err.entries.map(_.messageWithPath).mkString(","))

  object Session {
    val CookieName: String = config getString "akka.http.session.cookie.name"

    val CookieSecret: String = config getString "akka.http.session.server-secret"

    val Continuity: String = config getString "akka.http.session.continuity"

    val Transport: String = config getString "akka.http.session.transport"

    require(CookieName.nonEmpty, "akka.http.session.cookie.name must be non-empty")
    require(CookieSecret.nonEmpty, "akka.http.session.server-secret must be non-empty")
    require(Continuity.nonEmpty, "akka.http.session.continuity must be non-empty")
    require(Transport.nonEmpty, "akka.http.session.transport must be non-empty")
    require(
      Continuity == "one-off" || Continuity == "refreshable",
      "akka.http.session.continuity must be one-off or refreshable"
    )
    require(
      Transport == "cookie" || Transport == "header",
      "akka.http.session.transport must be cookie or header"
    )

    val SessionContinuityAndTransport: SessionType =
      Continuity match {
        case "one-off" =>
          Transport match {
            case "cookie" => SessionType.OneOffCookie
            case "header" => SessionType.OneOffHeader
          }
        case "refreshable" =>
          Transport match {
            case "cookie" => SessionType.RefreshableCookie
            case "header" => SessionType.RefreshableHeader
          }
      }
  }
}
