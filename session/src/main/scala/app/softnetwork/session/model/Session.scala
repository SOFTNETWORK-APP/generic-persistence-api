package app.softnetwork.session.model

import java.util.UUID

import com.softwaremill.session.{SessionSerializer, SessionManager, SessionConfig}
import app.softnetwork.persistence.model.ProtobufDomainObject
import app.softnetwork.session.config.Settings.Session._
import app.softnetwork.session.security.Crypto

import org.softnetwork.session.model.Session

import scala.collection.mutable
import scala.util.Try

/**
  * Created by smanciot on 29/04/2021.
  */
trait SessionData extends ProtobufDomainObject

trait SessionDecorator {_: Session =>

  import Session._

  private var dirty: Boolean = false

  def clear(): Session = {
    val theId = id
    data.clear()
    this += CookieName -> theId
  }

  def isDirty: Boolean = dirty

  def get(key: String): Option[String] = data.get(key)

  def isEmpty: Boolean = data.isEmpty

  def contains(key: String): Boolean = data.contains(key)

  def -=(key: String): Session = synchronized {
    dirty = true
    data -= key
    this
  }

  def +=(kv: (String, String)): Session = synchronized {
    dirty = true
    data += kv
    this
  }

  def apply(key: String): Any = data(key)

  lazy val id: String = data(CookieName)

  lazy val admin: Boolean = get(adminKey).exists(_.toBoolean)

  lazy val anonymous: Boolean = get(anonymousKey).exists(_.toBoolean)

  lazy val profile: Option[String] = get(profileKey)
}

trait SessionCompanion {
  val adminKey = "admin"

  val profileKey = "profile"

  val anonymousKey = "anonymous"

  val sessionConfig: SessionConfig = {
    SessionConfig.default(CookieSecret)
  }

  implicit val sessionManager: SessionManager[Session] = new SessionManager[Session](sessionConfig)

  def apply(): Session =
    Session.defaultInstance
      .withData(mutable.Map(CookieName -> UUID.randomUUID.toString))
      .withRefreshable(false)

  def apply(uuid: String): Session =
    Session.defaultInstance
      .withData(mutable.Map(CookieName -> uuid))
      .withRefreshable(false)

  implicit def sessionSerializer: SessionSerializer[Session, String] = new SessionSerializer[Session, String] {
    override def serialize(session: Session): String = {
      val encoded = java.net.URLEncoder
        .encode(session.data.filterNot(_._1.contains(":")).map(d => d._1 + ":" + d._2).mkString("\u0000"), "UTF-8")
      Crypto.sign(encoded, CookieSecret) + "-" + encoded
    }

    override def deserialize(data: String): Try[Session] = {
      def urldecode(data: String) =
        mutable.Map[String, String](
          java.net.URLDecoder
            .decode(data, "UTF-8")
            .split("\u0000")
            .map(_.split(":"))
            .map(p => p(0) -> p.drop(1).mkString(":")): _*
        )
      // Do not change this unless you understand the security issues behind timing attacks.
      // This method intentionally runs in constant time if the two strings have the same length.
      // If it didn't, it would be vulnerable to a timing attack.
      def safeEquals(a: String, b: String) = {
        if (a.length != b.length) false
        else {
          var equal = 0
          for (i <- Array.range(0, a.length)) {
            equal |= a(i) ^ b(i)
          }
          equal == 0
        }
      }

      Try {
        val splitted = data.split("-")
        val message  = splitted.tail.mkString("-")
        if (safeEquals(splitted(0), Crypto.sign(message, CookieSecret)))
          Session.defaultInstance.withData(urldecode(message))
        else
          throw new Exception("corrupted encrypted data")
      }
    }
  }
}