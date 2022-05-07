package app.softnetwork.utils

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File}
import java.nio.file.Files.copy
import java.nio.file.Paths.get
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.imageio.ImageIO
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 06/07/2018.
  */
object ImageTools {

  import MimeTypeTools._

  def encodeImageBase64(file: File, encodeAsURI: Boolean = false): Option[String] = {
    if (Option(file).isDefined) {
      import Base64Tools._
      val bos = new ByteArrayOutputStream()
      Try {
        val mimeType = detectMimeType(file)
        val format = toFormat(mimeType)
        ImageIO.write(ImageIO.read(file), format.getOrElse("jpeg"), bos)
        val encoded = encodeBase64(bos.toByteArray, if (encodeAsURI) mimeType else None)
        bos.close()
        encoded
      } match {
        case Success(s) => Some(s)
        case Failure(f) =>
          bos.close()
          None
      }
    }
    else {
      None
    }
  }

  def generateImages(file: File, replace: Boolean = true): Boolean = {
    detectMimeType(file) match {
      case Some(mimeType) if mimeType.startsWith("image") =>
        val originalPath = file.getAbsolutePath
        val format = toFormat(Some(mimeType)).getOrElse("jpeg")
        val src: BufferedImage = ImageIO.read(file)
        val originalWidth = src.getWidth
        val originalHeight = src.getHeight
        for (imageSize <- imageSizes.values) {
          resizeImage(src, originalWidth, originalHeight, originalPath, format, imageSize.width, imageSize.height, replace)
        }
        true
      case _ => false
    }
  }

  def getImage(file: File, size: Option[ImageSize] = None, replace: Boolean = true): File = {
    size match {
      case Some(s) =>
        val src: BufferedImage = ImageIO.read(file)
        val originalWidth = src.getWidth
        val originalHeight = src.getHeight
        val originalPath = file.getAbsolutePath
        val format = toFormat(file).getOrElse("jpeg")

        val width = s.width
        val height = s.height

        resizeImage(src, originalWidth, originalHeight, originalPath, format, width, height, replace)

      case _ => file
    }
  }

  private def resizeImage(
                           src: BufferedImage,
                           originalWidth: Int,
                           originalHeight: Int,
                           originalPath: String,
                           format: String,
                           width: Int,
                           height: Int,
                           replace: Boolean
                         ): File = {
    val out = new File(s"$originalPath.${width}x$height.$format")
    if (!out.exists() || replace) {
      if (width == originalWidth && height == originalHeight) {
        copy(get(originalPath), get(out.getAbsolutePath), REPLACE_EXISTING)
      } else {
        var imgWidth = width
        var imgHeight = height
        var topMargin = 0
        var leftMargin = 0
        if (originalWidth > originalHeight) {
          imgHeight = originalHeight * width / originalWidth
          topMargin = (imgWidth - imgHeight) / 2
        } else {
          imgWidth = originalWidth * height / originalHeight
          leftMargin = (imgHeight - imgWidth) / 2
        }

        val dest = Scalr.resize(src, Scalr.Method.ULTRA_QUALITY, imgWidth, imgHeight)
        val dest2 = Scalr.move(dest, leftMargin, topMargin, width, height, Color.WHITE)
        ImageIO.write(dest2, format, out)
      }
    }
    out
  }

  val SMALL = "SMALL"

  val ICON = "ICON"

  val imageSizes = Map[String, ImageSize](ICON -> Icon, SMALL -> Small)

  trait ImageSize {
    def width: Int

    def height: Int
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
