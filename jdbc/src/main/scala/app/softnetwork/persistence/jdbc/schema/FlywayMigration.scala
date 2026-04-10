package app.softnetwork.persistence.jdbc.schema

import app.softnetwork.persistence.jdbc.db.SlickDatabase
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

/** Runs Flyway migrations at startup for a specific table/entity.
  *
  * Migration files are loaded from classpath location `db/migration/{migrationFolder}/` following
  * Flyway naming convention: `V{version}__{description}.sql`
  *
  * Flyway metadata table is namespaced per entity to avoid conflicts:
  * `flyway_schema_history_{migrationFolder}`
  */
trait FlywayMigration { _: SlickDatabase =>

  protected lazy val migrationLogger: Logger =
    LoggerFactory.getLogger(getClass.getName)

  /** The folder name under `db/migration/` containing migration scripts. Typically the table name
    * (e.g., "api_keys", "organizations").
    */
  def migrationFolder: String

  /** Optional schema/dataset for the migration target. When set, Flyway uses this as the default
    * schema.
    */
  def migrationSchema: Option[String] = None

  /** Whether to baseline an existing database on first Flyway run. Set to `false` for strict V1
    * validation on fresh Flyway adoption. Default `true` for backward compatibility with pre-Flyway
    * databases.
    */
  def baselineOnMigrate: Boolean = true

  /** Runs pending Flyway migrations. Safe to call multiple times — already-applied migrations are
    * skipped.
    *
    * @throws org.flywaydb.core.api.FlywayException
    *   if migration fails (fail-fast: schema must be correct before app starts)
    */
  def migrate(): Unit = {
    Try {
      val builder = Flyway
        .configure()
        .dataSource(dataSource)
        .locations(s"db/migration/$migrationFolder")
        .table(s"flyway_schema_history_$migrationFolder")
        .baselineOnMigrate(baselineOnMigrate)
        .baselineVersion(MigrationVersion.fromVersion("0"))

      val configured = migrationSchema match {
        case Some(schema) => builder.defaultSchema(schema)
        case None         => builder
      }

      configured.load().migrate()
    } match {
      case Success(result) =>
        migrationLogger.info(
          s"Flyway migration for '$migrationFolder': " +
          s"${result.migrationsExecuted} migration(s) applied"
        )
      case Failure(f) =>
        migrationLogger.error(
          s"Flyway migration for '$migrationFolder' failed: ${f.getMessage}",
          f
        )
        throw f
    }
  }
}
