package app.softnetwork.persistence.jdbc.db

import akka.persistence.jdbc.db.SlickExtension
import akka.{actor => classic}
import app.softnetwork.io._
import app.softnetwork.utils.ClasspathResources
import com.typesafe.config.{Config, ConfigValueFactory}
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.{Database, Session}

import java.nio.file.{Files, Paths}
import java.sql.Statement
import java.util.concurrent.atomic.AtomicLong
import scala.util.{Failure, Success, Try}

object SlickDatabase {

  /** Config path of the HikariCP pool name, relative to the config handed to
    * [[akka.persistence.jdbc.db.SlickExtension]]. akka-persistence-jdbc builds every non-shared
    * pool via `slick.jdbc.JdbcBackend.Database.forConfig("slick.db", config)`, and Slick's
    * `HikariCPJdbcDataSource` reads its `poolName` from this exact path (defaulting to the path
    * itself, `"slick.db"`, when absent).
    */
  private[db] val PoolNamePath = "slick.db.poolName"

  /** Config path of the `slick.db` block. */
  private[db] val SlickDbPath = "slick.db"

  /** Monotonic counter (per class loader) that guarantees uniqueness of generated pool names even
    * when several instances of the same provider class are created in the same class loader.
    */
  private val poolNameCounter = new AtomicLong(0L)

  private[db] def nextPoolNameSuffix(): Long = poolNameCounter.incrementAndGet()

  /** Keep only characters that are safe both as a JMX `ObjectName` value (HikariCP registers the
    * pool MBean as `com.zaxxer.hikari:type=Pool (&lt;poolName&gt;)` without quoting) and as a
    * Prometheus label value. Anything else collapses to `_`.
    */
  private[db] def sanitize(s: String): String = {
    val cleaned = s.replaceAll("[^A-Za-z0-9._-]", "_")
    if (cleaned.isEmpty) "provider" else cleaned
  }
}

trait SlickDatabase extends ClasspathResources {

  override lazy val log: Logger = LoggerFactory getLogger getClass.getName

  implicit def classicSystem: classic.ActorSystem

  def config: Config

  /** Provides a DataSource for use by Flyway migrations. Extracted from the underlying HikariCP
    * connection pool. Override this method if using a non-HikariCP connection pool.
    */
  lazy val dataSource: javax.sql.DataSource = {
    db.source match {
      case hikari: slick.jdbc.hikaricp.HikariCPJdbcDataSource => hikari.ds
      case other =>
        throw new IllegalStateException(
          s"Expected HikariCP data source for Flyway, got ${other.getClass.getName}. " +
          "Configure slick-hikaricp or override the dataSource method."
        )
    }
  }

  lazy val slickProfile: String = config.getString("slick.profile")

  /** A unique HikariCP pool name for this provider's connection pool, of the form
    * `slick.db-&lt;providerClass&gt;-&lt;n&gt;`.
    *
    * Each trait that mixes in [[SlickDatabase]] (`JdbcSchema`, `JdbcStateProvider`,
    * `ColumnMappedJdbcStateProvider`, `JdbcOffsetProvider`, ...) builds its own non-shared pool. By
    * default akka-persistence-jdbc names them all `slick.db`, which collides in a single JVM the
    * moment `slick.db.registerMbeans = true` is set (only the first pool registers its MBean, the
    * rest fail with `JMX name (slick.db) is already registered`) and likewise breaks HikariCP's
    * native Prometheus tracker. Naming each pool distinctly removes the collision and enables
    * per-pool connection metrics.
    *
    * Override to customise the naming scheme.
    */
  protected def poolName: String = {
    // `Class.getSimpleName` can throw `InternalError` ("Malformed class name") on JDK 8 for some
    // synthetic/anonymous Scala class names, so fall back to the (always-safe) full name.
    val simpleName = Try(getClass.getSimpleName).toOption.getOrElse("")
    val cls = SlickDatabase.sanitize(if (simpleName.nonEmpty) simpleName else getClass.getName)
    s"slick.db-$cls-${SlickDatabase.nextPoolNameSuffix()}"
  }

  /** The config actually handed to [[akka.persistence.jdbc.db.SlickExtension]] when building
    * [[db]].
    *
    * When a `slick.db` block is present and no `poolName` has been configured explicitly, inject a
    * unique [[poolName]] so the resulting HikariCP pool is distinctly named. An explicitly
    * configured `slick.db.poolName` is left untouched, and any config without a `slick.db` block is
    * passed through unchanged.
    */
  private def dbConfig: Config = {
    if (config.hasPath(SlickDatabase.SlickDbPath) && !config.hasPath(SlickDatabase.PoolNamePath)) {
      val name = poolName
      log.debug(s"Assigning HikariCP pool name '$name' to ${getClass.getName}")
      config.withValue(SlickDatabase.PoolNamePath, ConfigValueFactory.fromAnyRef(name))
    } else {
      config
    }
  }

  lazy val db: Database = {
    log.info(slickProfile)
    val db = SlickExtension(classicSystem).database(dbConfig).database
    classicSystem.registerOnTermination(shutdown())
    db
  }

  def shutdown(): Unit = {
    log.info(s"Shutting down database")
    Try(db.shutdown) match {
      case Success(_) =>
      case Failure(f) => log.error(f.getMessage)
    }
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
