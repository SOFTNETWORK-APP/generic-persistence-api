package app.softnetwork.security

import org.apache.commons.codec.digest.Sha2Crypt

/**
  * Created by smanciot on 14/04/2018.
  */
sealed trait Encryption {
  def encrypt(clearText: String): String

  def checkEncryption(crypted: String, clearText: String): Boolean
}

object Sha512Encryption extends Encryption {

  def encrypt(clearText: String) = Sha2Crypt.sha512Crypt(clearText.getBytes("UTF-8").clone())

  def isEncrypted(crypted: String)  = crypted.startsWith("$6$")

  def checkEncryption(crypted: String, clearText: String) = {
    if(!isEncrypted(crypted))
      false
    else {
      val offset2ndDolar = crypted.indexOf('$', 1)
      if (offset2ndDolar < 0)
        false
      else {
        val offset3ndDolar = crypted.indexOf('$', offset2ndDolar + 1)
        if (offset3ndDolar < 0)
          false
        else {
          crypted.equals(Sha2Crypt.sha512Crypt(clearText.getBytes("UTF-8").clone(), crypted.substring(0, offset3ndDolar + 1)))
        }
      }
    }
  }
}

