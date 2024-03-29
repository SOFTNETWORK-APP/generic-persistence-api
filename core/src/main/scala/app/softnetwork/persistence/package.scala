package app.softnetwork

import java.util.{Date, UUID}
import app.softnetwork.security._
import app.softnetwork.persistence.model.Timestamped

import java.time.Instant
import scala.language.implicitConversions

/** Created by smanciot on 13/04/2020.
  */
package object persistence {

  trait ManifestWrapper[T] {
    protected case class ManifestW()(implicit val wrapped: Manifest[T])
    protected val manifestWrapper: ManifestW
  }

  def generateUUID(key: Option[String] = None): String =
    key match {
      case Some(clearText) => sha256(clearText)
      case _               => UUID.randomUUID().toString
    }

  def now(): Date = Date.from(Instant.now())

  def getType[T <: Timestamped](implicit m: Manifest[T]): String = {
    m.runtimeClass.getSimpleName.toLowerCase
  }

  /** Used for akka and elastic persistence ids, one per targeted environment (development,
    * production, ...)
    */
  val version: String = sys.env.getOrElse("VERSION", PersistenceCoreBuildInfo.version)

  val environment: String = sys.env.getOrElse(
    "TARGETED_ENV",
    if (version.endsWith("FINAL")) {
      "prod"
    } else {
      "dev"
    }
  )

  val appName: String = sys.env.getOrElse("APP", PersistenceCoreBuildInfo.name)
}
