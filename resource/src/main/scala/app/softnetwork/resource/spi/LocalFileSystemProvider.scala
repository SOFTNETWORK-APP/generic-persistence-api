package app.softnetwork.resource.spi

import app.softnetwork.persistence.environment
import app.softnetwork.resource.config.Settings.{BaseUrl, ImageSizes, ResourceDirectory, ResourcePath}
import app.softnetwork.utils.ImageTools.ImageSize
import app.softnetwork.utils.{Base64Tools, ImageTools, MimeTypeTools}
import com.typesafe.scalalogging.StrictLogging

import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

trait LocalFileSystemProvider extends ResourceProvider with StrictLogging {

  lazy val rootDir = s"$ResourceDirectory/$environment"

  /**
    * Upsert the underlying resource referenced by its uuid to the resource provider
    *
    * @param uuid - the uuid of the resource to upsert
    * @param data - the base64 encoded resource content
    * @param uri - the optional uri of the resource
    * @return whether the resource has been upserted or not
    */
  override def upsertResource(uuid: String, data: String, uri: Option[String] = None): Boolean = {
    Try {
      val root = Paths.get(rootDir)
      if (!Files.exists(root)) {
        Files.createDirectories(root)
      }
      val decoded = Base64Tools.decodeBase64(data)
      val path = Paths.get(rootDir, uri.getOrElse(""), uuid)
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
    * @param uri - the optional uri of the resource
    * @param content - the optional base64 encoded resource content
    * @param option  - the list of resource options
    * @return the optional path associated with this resource
    */
  override def loadResource(uuid: String, uri: Option[String], content: Option[String], option: ResourceOption*): Option[Path] = {
    val path = Paths.get(rootDir, uri.getOrElse(""), uuid)
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
        case Some(data) if upsertResource(uuid, data, uri) =>
          loadResource(uuid, uri, None, option:_*)
        case _ => None
      }
    }
  }

  /**
    * Deletes the underlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the resource to delete
    * @param uri - the optional uri of the resource
    * @return whether the operation is successful or not
    */
  override def deleteResource(uuid: String, uri: Option[String] = None): Boolean = {
    Try {
      val root = Paths.get(rootDir, uri.getOrElse(""))
      import java.util.stream.Collectors
      import scala.collection.JavaConverters._
      val listFiles: List[Path] =
        Files.list(root).filter(Files.isRegularFile(_)).filter { file =>
          file.getFileName.toString.startsWith(uuid)
        }.collect(Collectors.toList[Path]()).asScala.toList
      listFiles.foreach(path => Files.delete(path))
    } match {
      case Success(_) => true
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }

  val generatedImage: Regex = ".*(\\.\\d*x\\d*).*".r

  /**
    *
    * @param uri - the uri from which resources have to be listed
    * @return the resources located at this uri
    */
  override def listResources(uri: String): List[SimpleResource] = {
    Try {
      val dir = Paths.get(rootDir, uri)
      import java.util.stream.Collectors
      import scala.collection.JavaConverters._
      Files.list(dir)
        .filter(path => (Files.isRegularFile(path) && generatedImage.unapplySeq(path.getFileName.toString).isEmpty) ||
          Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).collect(Collectors.toList[Path]()).asScala.toList
    } match {
      case Success(files) => files.map(file => {
        val directory = Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)
        val name: String = file.getFileName.toString
        val image: Boolean = !directory && ImageTools.isAnImage(file)
        val url: String = {
          if(directory) {
            s"$BaseUrl/$ResourcePath/library/$uri/$name"
          }
          else if(image) {
            s"$BaseUrl/$ResourcePath/images/$uri/$name"
          }
          else {
            s"$BaseUrl/$ResourcePath/$uri/$name"
          }
        }
        SimpleResource(
          uri,
          name,
          directory,
          image,
          url
        )
      })
      case Failure(f) =>
        logger.error(f.getMessage, f)
        List.empty
    }
  }
}
