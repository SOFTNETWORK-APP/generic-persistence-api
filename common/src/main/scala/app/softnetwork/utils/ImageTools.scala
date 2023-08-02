package app.softnetwork.utils

import com.typesafe.scalalogging.StrictLogging

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.regex.Pattern
import javax.imageio.ImageIO
import scala.util.{Failure, Success, Try}

/** Created by smanciot on 06/07/2018.
  */
object ImageTools extends StrictLogging {

  import MimeTypeTools._

  val IMAGE_FORMAT: Pattern = Pattern.compile("(^image)(\\/)[a-zA-Z0-9_]*")

  def isAnImage(bytes: Array[Byte]): Boolean = {
    import MimeTypeTools._
    isAnImage(detectMimeType(bytes))
  }

  def isAnImage(path: Path): Boolean = {
    import MimeTypeTools._
    isAnImage(detectMimeType(path))
  }

  def isAnImage(mimeType: Option[String]): Boolean = {
    mimeType match {
      case Some(mimeType) =>
        val matcher = IMAGE_FORMAT.matcher(mimeType)
        matcher.find() && matcher.group(1) == "image"
      case _ => false
    }
  }

  def encodeImageBase64(path: Path, encodeAsURI: Boolean = false): Option[String] = {
    if (Option(path).isDefined) {
      import Base64Tools._
      val bos = new ByteArrayOutputStream()
      Try {
        val mimeType = detectMimeType(path)
        val format = toFormat(mimeType)
        ImageIO.write(ImageIO.read(Files.newInputStream(path)), format.getOrElse("jpeg"), bos)
        val encoded = encodeBase64(bos.toByteArray, if (encodeAsURI) mimeType else None)
        bos.close()
        encoded
      } match {
        case Success(s) => Some(s)
        case Failure(_) =>
          bos.close()
          None
      }
    } else {
      None
    }
  }

  def generateImages(
    originalPath: Path,
    imageSizes: Seq[ImageSize] = Seq(Icon, Small)
  ): Boolean = {
    detectMimeType(originalPath) match {
      case Some(mimeType) if isAnImage(Option(mimeType)) =>
        val format = toFormat(Some(mimeType)).getOrElse("jpeg")
        val sizes = imageSizes.filter(size => {
          val out = size.resizedPath(originalPath, Option(format))
          !Files.exists(out) || originalPath.toFile.lastModified() > out.toFile.lastModified()
        })
        if (sizes.nonEmpty) {
          logger.info(
            s"""Trying to resize image $originalPath to
               |${sizes.map(s => s"${s.width}x${s.height}").mkString(",")}""".stripMargin
          )
          Try(ImageIO.read(Files.newInputStream(originalPath))) match {
            case Success(src) =>
              val originalWidth = src.getWidth
              val originalHeight = src.getHeight
              for (size <- sizes) {
                resizeImage(
                  src,
                  originalWidth,
                  originalHeight,
                  originalPath,
                  format,
                  size
                )
              }
              true
            case Failure(f) =>
              logger.error(
                s"""an error occurred while trying to resize image $originalPath to
                   |${sizes.map(s => s"${s.width}x${s.height}").mkString(",")} :
                   |${f.getMessage}""".stripMargin
              )
              false
          }
        } else {
          true
        }
      case _ =>
        logger.error(
          s"""an error occurred while trying to resize $originalPath to
             |${imageSizes.map(s => s"${s.width}x${s.height}").mkString(",")}""".stripMargin
        )
        false
    }
  }

  def getImage(
    originalPath: Path,
    size: Option[ImageSize] = None
  ): Path = {
    size match {
      case Some(s) =>
        detectMimeType(originalPath) match {
          case Some(mimeType) if isAnImage(Option(mimeType)) =>
            val format = toFormat(originalPath).getOrElse("jpeg")
            val out = s.resizedPath(originalPath, Option(format))
            if (
              !Files.exists(out) || originalPath.toFile.lastModified() > out.toFile.lastModified()
            ) {
              Try(ImageIO.read(Files.newInputStream(originalPath))) match {
                case Success(src) =>
                  resizeImage(src, src.getWidth, src.getHeight, originalPath, format, s)
                case Failure(f) =>
                  logger.error(
                    s"""an error occurred while trying to resize image $originalPath to
                       |${s.width}x${s.height} :
                       |${f.getMessage}""".stripMargin
                  )
                  originalPath
              }
            } else {
              out
            }
          case _ =>
            logger.error(
              s"an error occurred while trying to resize $originalPath to ${s.width}x${s.height}"
            )
            originalPath
        }
      case _ => originalPath
    }
  }

  private def resizeImage(
    src: BufferedImage,
    originalWidth: Int,
    originalHeight: Int,
    originalPath: Path,
    format: String,
    imageSize: ImageSize
  ): Path = {
    import imageSize._
    var out = imageSize.resizedPath(originalPath, Option(format))
    if (width == originalWidth && height == originalHeight) {
      Files.copy(originalPath, out, REPLACE_EXISTING)
    } else {
      var imgWidth = width
      var imgHeight = height
      var topMargin = 0
      var leftMargin = 0
      if (originalWidth > originalHeight) {
        imgHeight = originalHeight * width / originalWidth
        topMargin = Math.abs(imgWidth - imgHeight) / 2
      } else {
        imgWidth = originalWidth * height / originalHeight
        leftMargin = Math.abs(imgHeight - imgWidth) / 2
      }

      val dest = Scalr.resize(src, Scalr.Method.ULTRA_QUALITY, imgWidth, imgHeight)
      val dest2 = Scalr.move(dest, leftMargin, topMargin, width, height, Color.WHITE)
      Try(ImageIO.write(dest2, format, Files.newOutputStream(out))) match {
        case Success(_) =>
        case Failure(f) =>
          logger.error(
            s"""an error occurred while trying to resize image $originalPath to
                 |${imageSize.width}x${imageSize.height} :
                 |${f.getMessage}""".stripMargin
          )
          if (!Files.exists(out)) {
            out = originalPath
          }
      }
    }
    out
  }

  trait ImageSize {
    def width: Int

    def height: Int

    def resizedPath(originalPath: Path, format: Option[String]): Path = {
      Paths.get(
        Seq(
          s"${originalPath.toAbsolutePath}",
          s".${width}x$height.",
          format.getOrElse(toFormat(originalPath).getOrElse("jpeg"))
        ).mkString("")
      )
    }
  }

  case object Icon extends ImageSize {
    val width = 32
    val height = 32
  }

  case object Small extends ImageSize {
    val width = 240
    val height = 240
  }

}
