package app.softnetwork

import java.math.BigInteger
import java.security.MessageDigest

/**
  * Created by smanciot on 10/05/2021.
  */
package object security {

  def md5(clearText: String): Array[Byte] = MessageDigest.getInstance("MD5").digest(clearText.getBytes).clone()

  def sha256(clearText: String): String =
    String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(clearText.getBytes("UTF-8"))))

}
