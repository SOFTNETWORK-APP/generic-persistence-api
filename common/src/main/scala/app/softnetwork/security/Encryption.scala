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

  def encrypt(clearText: String): String = Sha2Crypt.sha512Crypt(clearText.getBytes("UTF-8").clone())

  def isEncrypted(encrypted: String): Boolean = encrypted.startsWith("$6$")

  def checkEncryption(encrypted: String, clearText: String): Boolean = {
    encrypted.equals(hash(encrypted)(clearText))
  }

  def hash(encrypted: String): String => String = clearText => {
    if(!isEncrypted(encrypted)){
      clearText
    }
    else {
      val offset2ndDolar = encrypted.indexOf('$', 1)
      if (offset2ndDolar < 0)
        clearText
      else{
        val offset3ndDolar = encrypted.indexOf('$', offset2ndDolar + 1)
        if (offset3ndDolar < 0)
          clearText
        else{
          Sha2Crypt.sha512Crypt(clearText.getBytes("UTF-8").clone(), encrypted.substring(0, offset3ndDolar + 1))
        }
      }
    }
  }
}

