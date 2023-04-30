/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.softnetwork.utils

import app.softnetwork.io._
import org.slf4j.{Logger, LoggerFactory}

import java.io.InputStream

object ClasspathResources extends ClasspathResources {
  lazy val log: Logger = LoggerFactory getLogger getClass.getName
}

trait ClasspathResources {

  def log: Logger

  def fromClasspathAsString(file: String): Option[String] =
    fromClasspathAsStream(file).map(inputStreamToString)

  def fromClasspathAsStream(file: String): Option[InputStream] = Option(
    getClass.getClassLoader.getResourceAsStream(file)
  ) match {
    case None =>
      log.error(s"file $file not found in the classpath")
      None
    case some => some
  }

}
