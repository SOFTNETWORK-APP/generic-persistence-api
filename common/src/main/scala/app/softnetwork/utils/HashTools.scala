package app.softnetwork.utils

import org.apache.commons.codec.binary.Base64

import java.io._
import java.security.{DigestInputStream, MessageDigest}

/**
  * Created by smanciot on 06/07/2018.
  */
object HashTools {

  private val BufferSize = 8192

  def generateFileMD5(file: File): Option[String] = {
    hashFile(file, AlgorithmType.MD5)
  }

  def generateFileSHA1(file: File): Option[String] = {
    hashFile(file, AlgorithmType.SHA1)
  }

  def generateFileSHA256(file: File): Option[String] = {
    hashFile(file, AlgorithmType.SHA256)
  }

  def hashFile(file: File, algorithm: AlgorithmType.Value = AlgorithmType.MD5): Option[String] = {
    if (file.exists()) {
      val stream = new BufferedInputStream(new FileInputStream(file))
      hashStream(stream, algorithm)
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
    try {
      val dis = new DigestInputStream(stream, digest)
      val buffer = new Array[Byte](BufferSize)
      while (dis.read(buffer) >= 0) {}
      dis.close()
      Some(Base64.encodeBase64String(digest.digest()))
    } finally {
      stream.close()
    }
  }

  object AlgorithmType extends Enumeration {
    type AglorithmType = Value
    val MD5 = Value(0, "MD5")
    val SHA1 = Value(1, "SHA-1")
    val SHA256 = Value(2, "SHA-256")
  }
}
