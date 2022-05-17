package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.resource.model.Resource
import app.softnetwork.utils.{Base64Tools, HashTools, MimeTypeTools}
import org.json4s.Formats

import java.io.ByteArrayInputStream
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Paths}
import java.util.Date
import scala.util.{Failure, Success, Try}

protected[resource] trait LocalFileSystemResourceProvider extends GenericLocalFileSystemResourceProvider[Resource]
  with ManifestWrapper[Resource] {

  override protected val manifestWrapper: ManifestW = ManifestW()

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
