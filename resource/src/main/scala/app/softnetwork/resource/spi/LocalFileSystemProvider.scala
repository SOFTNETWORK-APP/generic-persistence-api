package app.softnetwork.resource.spi

import app.softnetwork.persistence.environment
import app.softnetwork.resource.config.Settings.{ImageSizes, ResourceDirectory}
import app.softnetwork.utils.ImageTools.ImageSize
import app.softnetwork.utils.{Base64Tools, ImageTools, MimeTypeTools}
import com.typesafe.scalalogging.StrictLogging

import java.nio.file.{Files, Path, Paths}
import scala.util.{Failure, Success, Try}

trait LocalFileSystemProvider extends ResourceProvider with StrictLogging {

  lazy val rootDir = s"$ResourceDirectory/$environment"

  /**
    * Upsert the underlying resource referenced by its uuid to the resource provider
    *
    * @param uuid - the uuid of the resource to upsert
    * @param data - the base64 encoded resource content
    * @return whether the resource has been upserted or not
    */
  override def upsertResource(uuid: String, data: String): Boolean = {
    Try {
      val root = Paths.get(rootDir)
      if (!Files.exists(root)) {
        Files.createDirectories(root)
      }
      val decoded = Base64Tools.decodeBase64(data)
      val path = Paths.get(rootDir, uuid)
      val fos = Files.newOutputStream(path)
      fos.write(decoded)
      fos.close()
      if (ImageTools.isAnImage(path)) {
        ImageTools.generateImages(path, replace = true, ImageSizes.values.toSeq)
      }
    } match {
      case Success(_) => true
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }

  /**
    *
    * @param uuid - the uuid of the resource to load
    * @param content - the optional base64 encoded resource content
    * @param option  - the list of resource options
    * @return the optional path associated with this resource
    */
  override def loadResource(uuid: String, content: Option[String], option: ResourceOption*): Option[Path] = {
    val path = Paths.get(rootDir, uuid)
    if (Files.exists(path)) {
      if(ImageTools.isAnImage(path)){
        val size: Option[ResourceOption] = option.find {
          case _: SizeOption => true
          case _ => false
        }
        size match {
          case Some(s) =>
            val imageSize: ImageSize = s.asInstanceOf[SizeOption].size
            import imageSize._
            val format = MimeTypeTools.toFormat(path).getOrElse("jpeg")
            val out = Paths.get(s"${path.toAbsolutePath}.${width}x$height.$format")
            if (Files.exists(out)) {
              Some(out)
            }
            else {
              Some(
                ImageTools.getImage(
                  path,
                  Option(imageSize),
                  replace = false
                )
              )
            }
          case _ => Some(path)
        }
      }
      else{
        Some(path)
      }
    }
    else {
      content match {
        case Some(data) if upsertResource(uuid, data) =>
          loadResource(uuid, None, option:_*)
        case _ => None
      }
    }
  }

}
