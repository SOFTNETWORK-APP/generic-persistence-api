package app.softnetwork.utils

import org.apache.commons.codec.binary.Base64

import java.io.{BufferedInputStream, ByteArrayInputStream, InputStream}
import java.nio.file.{Files, Path}
import java.security.{DigestInputStream, MessageDigest}
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 06/07/2018.
  */
object HashTools {

  private val BufferSize = 8192

  def generateFileMD5(path: Path): Option[String] = {
    hashFile(path, AlgorithmType.MD5)
  }

  def generateFileSHA1(path: Path): Option[String] = {
    hashFile(path, AlgorithmType.SHA1)
  }

  def generateFileSHA256(path: Path): Option[String] = {
    hashFile(path, AlgorithmType.SHA256)
  }

  def hashFile(path: Path, algorithm: AlgorithmType.Value = AlgorithmType.MD5): Option[String] = {
    if (Files.exists(path)) {
      hashStream(new BufferedInputStream(Files.newInputStream(path)), algorithm)
    } else {
      None
    }
  }

  def hashString(data: String, algorithm: AlgorithmType.Value = AlgorithmType.MD5): Option[String] = {
    val stream = new ByteArrayInputStream(data.getBytes("UTF-8"))
    hashStream(stream, algorithm)
  }

  def hashStream(stream: InputStream, algorithm: AlgorithmType.Value = AlgorithmType.MD5): Option[String] = {
    val digest = MessageDigest.getInstance(algorithm.toString)
    Try {
      val dis = new DigestInputStream(stream, digest)
      val buffer = new Array[Byte](BufferSize)
      while (dis.read(buffer) >= 0) {}
      dis.close()
      stream.close()
      Some(Base64.encodeBase64String(digest.digest()))
    } match {
      case Success(s) => s
      case Failure(_) => None
    }
  }

  object AlgorithmType extends Enumeration {
    type AglorithmType = Value
    val MD5: AlgorithmType.Value = Value(0, "MD5")
    val SHA1: AlgorithmType.Value = Value(1, "SHA-1")
    val SHA256: AlgorithmType.Value = Value(2, "SHA-256")
  }
}
