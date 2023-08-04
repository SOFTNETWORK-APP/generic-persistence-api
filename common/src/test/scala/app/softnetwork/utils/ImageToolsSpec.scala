package app.softnetwork.utils

import app.softnetwork.utils.ImageTools.{Icon, Small}
import app.softnetwork.utils.MimeTypeTools.toFormat
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.file.{Files, Path, Paths}

class ImageToolsSpec extends AnyWordSpecLike {

  val path: Path =
    Paths.get(Thread.currentThread().getContextClassLoader.getResource("mars.jpeg").getPath)

  val format: String = toFormat(path).getOrElse("jpeg")

  "ImageTools" should {
    "check if it is an image" in {
      assert(ImageTools.isAnImage(path))
    }
    "encode an image as Base64" in {
      val encoded = ImageTools.encodeImageBase64(path, encodeAsURI = true)
      assert(encoded.nonEmpty)
      assert(encoded.exists(_.startsWith("data:image/jpeg;base64,/")))
    }
    "resize an image" in {
      val sizes = Seq(Icon)
      assert(ImageTools.generateImages(path, sizes))
      for (size <- sizes) {
        assert(Files.exists(size.resizedPath(path, Option(format))))
      }
    }
    "get an image with the desired size" in {
      val out = Small.resizedPath(path, Option(format))
      assert(ImageTools.getImage(path, Some(Small)) == out)
      assert(Files.exists(out))
    }
  }
}
