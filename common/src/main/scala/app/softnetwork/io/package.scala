package app.softnetwork

import java.io.InputStream
import scala.io.Source
import scala.language.implicitConversions

package object io {
  implicit def inputStreamToString(is: InputStream): String = Source.fromInputStream(is).mkString
}
