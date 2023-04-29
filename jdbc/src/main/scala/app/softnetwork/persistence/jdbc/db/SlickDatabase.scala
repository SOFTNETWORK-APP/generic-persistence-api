package app.softnetwork.persistence.jdbc.db

import akka.persistence.jdbc.db.SlickExtension
import akka.{actor => classic}
import app.softnetwork.io._
import app.softnetwork.utils.ClasspathResources
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.{Database, Session}

import java.nio.file.{Files, Paths}
import java.sql.Statement
import scala.util.{Failure, Success, Try}

trait SlickDatabase extends ClasspathResources {

  override lazy val log: Logger = LoggerFactory getLogger getClass.getName

  implicit def classicSystem: classic.ActorSystem

  def config: Config

  lazy val profile: String = config.getString("slick.profile")

  lazy val db: Database = {
    log.info(profile)
    val slickDatabase = SlickExtension(classicSystem).database(config)
    slickDatabase.profile
    val db = slickDatabase.database
    classicSystem.registerOnTermination(db.shutdown)
    db
  }

  sys.addShutdownHook(shutdown())

  def shutdown(): Unit = {
    log.info(s"Shutting down database")
    db.shutdown
  }

  def withFile(file: String, separator: String = ";"): Unit = {
    log.info(s"executing script from file $file")
    val p = Paths.get(file)
    val source: Option[String] = {
      if (Files.exists(p)) {
        Try(Files.newInputStream(p)) match {
          case Failure(exception) =>
            log.error(exception.getMessage)
            None
          case Success(value) => Some(value)
        }
      } else {
        fromClasspathAsString(file)
      }
    }
    withSource(source, separator)
  }

  def withSource(source: Option[String], separator: String = ";"): Unit = {
    for {
      content <- source
      ddl <- for {
        trimmedLine <- content.split(separator) map (_.trim)
        if trimmedLine.nonEmpty
      } yield trimmedLine
    } withStatement { stmt =>
      try {
        val ret = stmt.executeUpdate(ddl)
        log.info(s"executing statement \n$ddl\n\t-> $ret")
      } catch {
        case t: java.sql.SQLSyntaxErrorException
            if t.getMessage contains "ORA-00942" => // suppress known error message in the test
        case other: Throwable => log.error(other.getMessage)
      }
    }
  }

  def withDatabase[A](f: Database => A): A = f(db)

  def withSession[A](f: Session => A): A = {
    withDatabase { db =>
      val session = db.createSession()
      try f(session)
      finally session.close()
    }
  }

  def withStatement[A](f: Statement => A): A = withSession(session => session.withStatement()(f))

}
