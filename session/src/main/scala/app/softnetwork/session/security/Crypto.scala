package app.softnetwork.session.security

/**
  * Created by smanciot on 21/03/2018.
  */
import javax.crypto._
import javax.crypto.spec.SecretKeySpec

trait Crypto {

  def sign(message: String, key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message.getBytes("utf-8")))
  }

  def sign(message: String, secret: String): String =
    sign(message, secret.getBytes("utf-8"))

}

object Crypto extends Crypto
