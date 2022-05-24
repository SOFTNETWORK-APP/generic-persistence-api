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

package app.softnetwork.persistence.jdbc.query

import java.sql.Statement
import akka.persistence.jdbc.config.SlickConfiguration
import akka.persistence.jdbc.util.SlickDatabase
import app.softnetwork.utils.ClasspathResources
import app.softnetwork.persistence.query.SchemaProvider
import com.typesafe.config.{Config, ConfigFactory}
import app.softnetwork.persistence.jdbc.query.JdbcSchema.{H2, Postgres, SchemaType}
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.JdbcBackend.{Database, Session}


trait JdbcSchemaProvider extends SchemaProvider with ClasspathResources with StrictLogging {

  def schemaType: SchemaType

  def cfg: Config = ConfigFactory.load()

  def db: Database = SlickDatabase.forConfig(cfg, new SlickConfiguration(cfg.getConfig("slick")))

  def initSchema(): Unit = create(schemaType.schema)

  def withDatabase[A](f: Database => A): A = f(db)

  def withSession[A](f: Session => A): A = {
    withDatabase { db =>
      val session = db.createSession()
      try f(session) finally session.close()
    }
  }

  def withStatement[A](f: Statement => A): A = withSession(session => session.withStatement()(f))

  private[this] def create(schema: String, separator: String = ";"): Unit = for {
    schema <- Option(fromClasspathAsString(schema))
    ddl <- for {
      trimmedLine <- schema.split(separator) map (_.trim)
      if trimmedLine.nonEmpty
    } yield trimmedLine
  } withStatement { stmt =>
    try stmt.executeUpdate(ddl) catch {
      case t: java.sql.SQLSyntaxErrorException if t.getMessage contains "ORA-00942" => // suppress known error message in the test
      case other: Throwable => logger.error(other.getMessage)
    }
  }

}

trait PostgresSchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = Postgres
}

trait H2SchemaProvider extends JdbcSchemaProvider {
  override lazy val schemaType: SchemaType = H2
}
