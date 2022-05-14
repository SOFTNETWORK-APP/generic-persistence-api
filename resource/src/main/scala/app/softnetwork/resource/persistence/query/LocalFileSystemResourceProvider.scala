package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.query.ExternalPersistenceProvider
import app.softnetwork.persistence.{ManifestWrapper, environment}
import app.softnetwork.resource.config.Settings.{ImageSizes, ResourceDirectory}
import app.softnetwork.resource.model.Resource
import app.softnetwork.resource.spi.{ResourceOption, ResourceProvider, SizeOption}
import app.softnetwork.utils.ImageTools.ImageSize
import app.softnetwork.utils.{Base64Tools, HashTools, ImageTools, MimeTypeTools}
import com.typesafe.scalalogging.StrictLogging
import org.json4s.Formats

import java.io.ByteArrayInputStream
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.util.Date
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

protected[resource] trait LocalFileSystemResourceProvider extends ResourceProvider with ExternalPersistenceProvider[Resource] with ManifestWrapper[Resource] with StrictLogging {

  lazy val rootDir = s"$ResourceDirectory/$environment"

  override protected val manifestWrapper: ManifestW = ManifestW()

  /**
    * Creates the underlying document to the external system
    *
    * @param document - the document to create
    * @param t        - implicit ClassTag for T
    * @return whether the operation is successful or not
    */
  override def createDocument(document: Resource)(implicit t: ClassTag[Resource]): Boolean = {
    import document._
    upsertDocument(uuid, content)
  }

  /**
    * Updates the underlying document to the external system
    *
    * @param document - the document to update
    * @param upsert   - whether or not to create the underlying document if it does not exist in the external system
    * @param t        - implicit ClassTag for T
    * @return whether the operation is successful or not
    */
  override def updateDocument(document: Resource, upsert: Boolean = true)(implicit t: ClassTag[Resource]): Boolean = {
    import document._
    upsertDocument(uuid, content)
  }

  /**
    * Upsert the underlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to upsert
    * @param data - the document data base64 encoded
    * @return whether the operation is successful or not
    */
  override def upsertDocument(uuid: String, data: String): Boolean = {
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
    * Deletes the underlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to delete
    * @return whether the operation is successful or not
    */
  override def deleteDocument(uuid: String): Boolean = {
    Try {
      val root = Paths.get(rootDir)
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

  /**
    * Load the document referenced by its uuid
    *
    * @param uuid - the document uuid
    * @param m    - implicit Manifest for T
    * @return the document retrieved, None otherwise
    */
  override def loadDocument(uuid: String)(implicit m: Manifest[Resource], formats: Formats): Option[Resource] = {
    Try {
      val path = Paths.get(rootDir, uuid)
      if (Files.exists(path)) {
        val fileAttributes = Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
        val bytes = Files.readAllBytes(path)
        val content = Base64Tools.encodeBase64(bytes)
        val md5 = HashTools.hashStream(
          new ByteArrayInputStream(
            bytes
          )
        ).getOrElse("")
        Some(
          Resource.defaultInstance
            .withUuid(uuid)
            .withContent(content)
            .withMd5(md5)
            .withCreatedDate(new Date(fileAttributes.creationTime().toMillis))
            .withLastUpdated(new Date(fileAttributes.lastModifiedTime().toMillis))
            .copy(mimetype = MimeTypeTools.detectMimeType(path))
        )
      }
      else {
        None
      }
    } match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        None
    }
  }

  /**
    *
    * @param uuid    - the resource uuid
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
        case Some(data) if upsertDocument(uuid, data) =>
          loadResource(uuid, None, option:_*)
        case _ => None
      }
    }
  }

}
