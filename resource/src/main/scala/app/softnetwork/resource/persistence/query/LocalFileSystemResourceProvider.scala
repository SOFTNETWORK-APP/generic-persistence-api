package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.query.ExternalPersistenceProvider
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.resource.model.Resource
import app.softnetwork.resource.spi.LocalFileSystemProvider
import app.softnetwork.utils.{Base64Tools, HashTools, MimeTypeTools}
import org.json4s.Formats

import java.io.ByteArrayInputStream
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.util.Date
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

protected[resource] trait LocalFileSystemResourceProvider extends LocalFileSystemProvider
  with ExternalPersistenceProvider[Resource]
  with ManifestWrapper[Resource] {

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
    upsertResource(uuid, data)
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

}
