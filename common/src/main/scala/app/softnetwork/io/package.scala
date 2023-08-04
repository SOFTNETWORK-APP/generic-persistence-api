package app.softnetwork

import java.io.InputStream
import scala.io.Source
import scala.language.implicitConversions
import scala.util.control.NonFatal

package object io {
  implicit def inputStreamToString(is: InputStream): String = Source.fromInputStream(is).mkString

  def withResources[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
    val resource: T = r
    require(resource != null, "resource is null")
    var exception: Throwable = null
    try {
      f(resource)
    } catch {
      case NonFatal(e) =>
        exception = e
        throw e
    } finally {
      closeAndAddSuppressed(exception, resource)
    }
  }

  private def closeAndAddSuppressed(e: Throwable, resource: AutoCloseable): Unit = {
    if (e != null) {
      try {
        resource.close()
      } catch {
        case NonFatal(suppressed) =>
          e.addSuppressed(suppressed)
      }
    } else {
      resource.close()
    }
  }
}
