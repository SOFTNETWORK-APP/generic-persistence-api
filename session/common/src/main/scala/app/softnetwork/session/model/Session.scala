package app.softnetwork.session.model

import java.util.UUID
import com.softwaremill.session.{
  JValueSessionSerializer,
  JwtSessionEncoder,
  SessionConfig,
  SessionEncoder,
  SessionManager,
  SessionSerializer
}
import app.softnetwork.persistence.model.ProtobufDomainObject
import app.softnetwork.session.config.Settings.Session._
import app.softnetwork.session.security.Crypto
import org.json4s.{Formats, JValue}
import org.softnetwork.session.model.Session

import scala.language.implicitConversions
import scala.util.Try

/** Created by smanciot on 29/04/2021.
  */
trait SessionData extends ProtobufDomainObject

trait SessionDecorator { self: Session =>

  import Session._

  private var dirty: Boolean = false

  def clear(): Session = {
    val theId = id
    withKvs(Map.empty[String, String] + (SessionName -> theId))
  }

  def isDirty: Boolean = dirty

  def get(key: String): Option[String] = kvs.get(key)

  def isEmpty: Boolean = kvs.isEmpty

  def contains(key: String): Boolean = kvs.contains(key)

  def -(key: String): Session =
    synchronized {
      dirty = true
      withKvs(kvs - key)
    }

  def +(kv: (String, String)): Session =
    synchronized {
      dirty = true
      withKvs(kvs + kv)
    }

  def ++(kvs: Seq[(String, String)]): Session =
    synchronized {
      dirty = true
      withKvs(this.kvs ++ kvs)
    }

  def apply(key: String): Any = kvs(key)

  lazy val id: String = kvs(SessionName)

  lazy val admin: Boolean = get(adminKey).exists(_.toBoolean)

  lazy val anonymous: Boolean = get(anonymousKey).exists(_.toBoolean)

  lazy val profile: Option[String] = get(profileKey)
}

trait SessionCompanion {
  val adminKey = "admin"

  val profileKey = "profile"

  val anonymousKey = "anonymous"

  val SessionName: String = DefaultSessionConfig.sessionCookieConfig.name

  val _refreshable: Boolean = Continuity match {
    case "refreshable" => true
    case _             => false
  }

  def apply(): Session =
    Session.defaultInstance
      .withKvs(Map(SessionName -> UUID.randomUUID.toString))
      .withRefreshable(_refreshable)

  def apply(uuid: String): Session =
    Session.defaultInstance
      .withKvs(Map(SessionName -> uuid))
      .withRefreshable(_refreshable)

}

class BasicSessionSerializer(val serverSecret: String) extends SessionSerializer[Session, String] {

  override def serialize(session: Session): String = {
    val encoded = java.net.URLEncoder
      .encode(
        session.kvs
          .filterNot(_._1.contains(":"))
          .map(d => d._1 + ":" + d._2)
          .mkString("\u0000"),
        "UTF-8"
      )
    Crypto.sign(encoded, serverSecret) + "-" + encoded
  }

  override def deserialize(kvs: String): Try[Session] = {
    def urldecode(kvs: String) =
      Map[String, String](
        java.net.URLDecoder
          .decode(kvs, "UTF-8")
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
      val split = kvs.split("-")
      val message = split.tail.mkString("-")
      if (safeEquals(split(0), Crypto.sign(message, serverSecret)))
        Session.defaultInstance.withKvs(urldecode(message))
      else
        throw new Exception("corrupted encrypted kvs")
    }
  }
}

object SessionSerializers {

  def basic(secret: String): SessionSerializer[Session, String] =
    new BasicSessionSerializer(secret)

  def jwt(implicit formats: Formats): SessionSerializer[Session, JValue] =
    JValueSessionSerializer.caseClass[Session]
}

object SessionManagers {

  def basic(implicit sessionConfig: SessionConfig): SessionManager[Session] = {
    import SessionEncoder._
    implicit val serializer: SessionSerializer[Session, String] =
      SessionSerializers.basic(sessionConfig.serverSecret)
    new SessionManager[Session](sessionConfig)
  }

  def jwt(implicit sessionConfig: SessionConfig, formats: Formats): SessionManager[Session] = {
    implicit val serializer: SessionSerializer[Session, JValue] = SessionSerializers.jwt(formats)
    implicit val encoder: JwtSessionEncoder[Session] = new JwtSessionEncoder[Session]
    new SessionManager[Session](sessionConfig)(encoder)
  }
}
