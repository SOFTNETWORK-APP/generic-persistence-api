package app.softnetwork.utils

import app.softnetwork.utils.ImageTools.{Icon, Small}
import app.softnetwork.utils.MimeTypeTools.toFormat
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.file.{Files, Path, Paths}

class ImageToolsSpec extends AnyWordSpecLike {

  val path: Path =
    Paths.get(Thread.currentThread().getContextClassLoader.getResource("mars.jpeg").getPath)

  "ImageTools" should {
    "check if it is an image" in {
      assert(ImageTools.isAnImage(path))
    }
    "resize an image" in {
      val sizes = Seq(Icon, Small)
      assert(ImageTools.generateImages(path, replace = false, sizes))
      val format = toFormat(path).getOrElse("jpeg")
      for (size <- sizes) {
        assert(Files.exists(size.resizedPath(path, Option(format))))
      }
    }
  }
}
