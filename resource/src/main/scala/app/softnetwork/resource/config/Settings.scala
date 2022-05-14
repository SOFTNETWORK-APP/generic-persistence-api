package app.softnetwork.resource.config

import app.softnetwork.utils.ImageTools.ImageSize
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

object Settings  extends StrictLogging {

  lazy val config: Config = ConfigFactory.load()

  val BaseUrl: String = config.getString("resource.baseUrl")

  val ResourcePath: String = config.getString("resource.path")

  val ResourceDirectory: String = config.getString("resource.directory")

  import scala.collection.JavaConverters._

  val ImageSizes: Map[String, ImageSize] = config.getStringList("resource.images.sizes").asScala.toList.map(size =>
    size.toLowerCase -> Configs[Size].get(config, s"resource.images.$size").value
  ).toMap

  case class Size(width: Int, height: Int) extends ImageSize
}
