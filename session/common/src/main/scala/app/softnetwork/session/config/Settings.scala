package app.softnetwork.session.config

/** Created by smanciot on 21/03/2018.
  */
import com.softwaremill.session.SessionConfig
import com.typesafe.config.{Config, ConfigFactory}
import configs.ConfigError
import org.softnetwork.session.model.Session.SessionType

object Settings {
  lazy val config: Config = ConfigFactory.load()

  def configErrorsToException: ConfigError => Throwable = err =>
    new IllegalStateException(err.entries.map(_.messageWithPath).mkString(","))

  object Session {

    val DefaultSessionConfig: SessionConfig = SessionConfig.fromConfig(config)
    require(
      DefaultSessionConfig.serverSecret.nonEmpty,
      "akka.http.session.server-secret must not be empty"
    )

    val Continuity: String = (config getString "akka.http.session.continuity").toLowerCase
    require(Continuity.nonEmpty, "akka.http.session.continuity must not be empty")
    require(
      Continuity == "one-off" || Continuity == "refreshable",
      "akka.http.session.continuity must be one-off or refreshable"
    )

    val Transport: String = (config getString "akka.http.session.transport").toLowerCase
    require(Transport.nonEmpty, "akka.http.session.transport must not be empty")
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
