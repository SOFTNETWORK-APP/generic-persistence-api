package app.softnetwork.config

import com.typesafe.config.{Config, ConfigException, ConfigFactory}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** Created by smanciot on 04/05/2021.
  */
object Settings {

  lazy val config: Config =
    ConfigFactory.load().withFallback(ConfigFactory.load("softnetwork-common.conf"))

  val DefaultTimeout: FiniteDuration = config.getInt("softnetwork.default-timeout").seconds

  val MustacheRootPath: Option[String] =
    Try(config.getString("softnetwork.mustache.root.path")) match {
      case Success(s) => Some(s)
      case Failure(f) =>
        f match {
          case _: ConfigException.Missing => None
          case other                      => throw other
        }
    }

}
