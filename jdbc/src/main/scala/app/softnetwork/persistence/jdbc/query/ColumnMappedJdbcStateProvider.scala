package app.softnetwork.persistence.jdbc.query

import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence._
import app.softnetwork.persistence.jdbc.db.SlickDatabase
import app.softnetwork.persistence.jdbc.schema.FlywayMigration
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.persistence.query.ExternalPersistenceProvider
import app.softnetwork.serialization.{commonFormats, serialization}
import org.json4s.Formats
import slick.jdbc.{GetResult, JdbcProfile}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** A state provider that maps entity fields to individual typed table columns, enabling indexed
  * lookups and SQL queries on specific fields.
  *
  * Unlike `JdbcStateProvider` which stores the entire state as a JSON blob, this provider requires
  * the implementor to define:
  *   - A Slick `Table` class with typed columns (`TableType`)
  *   - `toRow(T)` / `fromRow(Row)` conversions
  *   - An implicit `GetResult[RowType]` for `searchDocuments` raw SQL
  *
  * Schema DDL is managed by Flyway migrations (not Slick DDL auto-generation).
  *
  * @tparam T
  *   the entity state type
  */
trait ColumnMappedJdbcStateProvider[T <: Timestamped]
    extends ExternalPersistenceProvider[T]
    with SlickDatabase
    with FlywayMigration
    with Completion {
  _: ManifestWrapper[T] with JdbcProfile =>

  import api._

  // --- Abstract type members ---

  /** The row type used by the Slick Table definition. Typically a tuple matching the table columns.
    */
  type RowType

  /** The concrete Slick Table class. */
  type TableType <: Table[RowType]

  // --- Abstract members ---

  /** The Slick TableQuery for this entity's table. */
  def tableQuery: TableQuery[TableType]

  /** Convert an entity state to a table row. */
  def toRow(entity: T, deleted: Boolean = false): RowType

  /** Convert a table row to an entity state. Return None if the row represents a
    * deleted/soft-deleted entity.
    */
  def fromRow(row: RowType): Option[T]

  /** Return the uuid column from the Table definition, used for filtering. */
  def rowUuidColumn(row: TableType): Rep[String]

  /** Implicit `GetResult` for deserializing raw SQL result rows in `searchDocuments`. Must match
    * the column order of the table.
    */
  implicit def getResult: GetResult[RowType]

  // --- Concrete members ---

  implicit def formats: Formats = commonFormats

  /** The table name, used for raw SQL search queries and logging. Derived from the entity type
    * name, converted to snake_case.
    */
  lazy val tableName: String =
    manifestWrapper.wrapped.runtimeClass.getSimpleName
      .replaceAll("([A-Z])", "_$1")
      .toLowerCase
      .stripPrefix("_")

  def excludedFields: Set[String] = Set("__serializedSizeMemoized")

  /** Optional schema/dataset prefix for qualified table name. */
  def dataset: Option[String] = {
    if (config.hasPath("jdbc-external-processor.dataset")) {
      val d = config.getString("jdbc-external-processor.dataset")
      if (d.nonEmpty) Some(d) else None
    } else {
      None
    }
  }

  /** Defaults to `tableName` so that Flyway migration folder and table name stay consistent.
    * Override only when they intentionally differ.
    */
  override def migrationFolder: String = tableName

  override def migrationSchema: Option[String] = dataset

  private[this] lazy val tableFullName: String = dataset match {
    case Some(s) => s"$s.$tableName"
    case _       => tableName
  }

  /** Column list for `searchDocuments` raw SQL. Override with explicit column names (e.g., `"uuid,
    * last_updated, name, email, status, deleted"`) when adding Flyway migrations that change column
    * order, to keep `GetResult` aligned.
    */
  protected def selectColumns: String = "*"

  implicit def executionContext: ExecutionContext

  // --- CRUD operations ---

  override final def createDocument(document: T)(implicit t: ClassTag[T]): Boolean = {
    val row = toRow(document)
    val action = (tableQuery += row).map(_ > 0)
    db.run(action).complete() match {
      case Success(value) =>
        log.debug(s"Insert $tableFullName ${document.uuid} -> $value")
        value
      case Failure(f) =>
        log.error(f.getMessage, f)
        false
    }
  }

  override final def updateDocument(document: T, upsert: Boolean = true)(implicit
    t: ClassTag[T]
  ): Boolean = {
    if (upsert) {
      loadDocument(document.uuid) match {
        case Some(_) => doUpdate(document)
        case None    => createDocument(document)
      }
    } else {
      doUpdate(document)
    }
  }

  private def doUpdate(document: T): Boolean = {
    val row = toRow(document)
    val action = tableQuery
      .filter(r => rowUuidColumn(r) === document.uuid)
      .update(row)
      .map(_ > 0)
    db.run(action).complete() match {
      case Success(value) =>
        log.debug(s"Update $tableFullName ${document.uuid} -> $value")
        value
      case Failure(f) =>
        log.error(f.getMessage, f)
        false
    }
  }

  override final def upsertDocument(uuid: String, data: String): Boolean = {
    implicit val manifest: Manifest[T] = manifestWrapper.wrapped
    loadDocument(uuid) match {
      case Some(existing) =>
        var state = serialization.read[Map[String, Any]](
          serialization.write(existing)
        )
        val updatedState = serialization.read[Map[String, Any]](data)
        for ((key, value) <- updatedState) {
          if (!excludedFields.contains(key)) {
            state = state + (key -> value)
          }
        }
        Try(
          serialization.read[T](serialization.write(state))(formats, manifest)
        ) match {
          case Success(updated) => updateDocument(updated)
          case Failure(e) =>
            log.error(s"Failed to upsert $uuid with data $data", e)
            false
        }
      case None =>
        Try(
          serialization.read[T](data)(formats, manifestWrapper.wrapped)
        ) match {
          case Success(entity) => createDocument(entity)
          case Failure(e) =>
            log.error(s"Failed to create $uuid from data $data", e)
            false
        }
    }
  }

  /** Soft-deletes the document by setting deleted=true via `toRow(entity, deleted=true)`.
    *
    * Returns `true` if the entity was found and soft-deleted, or if the entity was already absent
    * (idempotent — safe for `State2ExternalProcessorStream` which treats `false` as a failure
    * requiring retry).
    *
    * The load-then-update is intentionally tolerant of concurrent removal: if another
    * thread/process deletes the row between `loadDocument` and the UPDATE, the update affects 0
    * rows but we still return `true` because the end state (entity gone) is what the caller wanted.
    */
  override final def deleteDocument(uuid: String): Boolean = {
    loadDocument(uuid) match {
      case Some(entity) =>
        val row = toRow(entity, deleted = true)
        val action = tableQuery
          .filter(r => rowUuidColumn(r) === uuid)
          .update(row)
        db.run(action).complete() match {
          case Success(_) =>
            // Whether count > 0 or == 0, the entity is effectively deleted.
            // count == 0 means another thread/process removed it between
            // loadDocument and this update — same desired end state.
            log.debug(s"Delete $tableFullName $uuid -> true")
            true
          case Failure(f) =>
            log.error(f.getMessage, f)
            false
        }
      case None =>
        log.debug(s"Delete $tableFullName $uuid -> already absent, no-op")
        true
    }
  }

  /** Hard-deletes the document by physically removing the row. Use for GDPR compliance or permanent
    * data removal.
    *
    * Note: this method is not part of the `ExternalPersistenceProvider` interface and must be
    * called on the concrete provider type. For pipeline-driven hard deletes, downcast the provider
    * or use a dedicated command handler.
    */
  override def destroy(uuid: String): Boolean = {
    val action = tableQuery.filter(r => rowUuidColumn(r) === uuid).delete.map(_ > 0)
    db.run(action).complete() match {
      case Success(value) =>
        log.debug(s"Destroy $tableFullName $uuid -> $value")
        value
      case Failure(f) =>
        log.error(f.getMessage, f)
        false
    }
  }

  override final def loadDocument(
    uuid: String
  )(implicit m: Manifest[T], formats: Formats): Option[T] = {
    val action = tableQuery
      .filter(r => rowUuidColumn(r) === uuid)
      .result
      .headOption
    db.run(action).complete() match {
      case Success(Some(row)) => fromRow(row)
      case Success(None) =>
        log.debug(s"Load $tableFullName $uuid -> None")
        None
      case Failure(f) =>
        log.error(f.getMessage, f)
        None
    }
  }

  /** Search documents using a SQL WHERE clause against typed columns.
    *
    * Unlike `JdbcStateProvider.search` which queries the JSON `state` column, this queries real
    * typed columns — enabling indexed lookups.
    *
    * **Security:** The `query` string is interpolated directly into SQL (same design as
    * `JdbcStateProvider`). This is safe when queries come from internal code (event processors).
    * NEVER pass user-supplied input. For external input, use type-safe Slick finder methods.
    */
  override final def searchDocuments(
    query: String
  )(implicit m: Manifest[T], formats: Formats): List[T] = {
    val action = sql"""SELECT #$selectColumns FROM #$tableFullName WHERE #$query"""
      .as[RowType]
    db.run(action).complete() match {
      case Success(rows) =>
        log.debug(
          s"Search $tableFullName with $query -> ${rows.size} result(s)"
        )
        rows.flatMap(fromRow).toList
      case Failure(f) =>
        log.error(f.getMessage, f)
        Nil
    }
  }

  /** Validates that a string is a safe SQL identifier (letters, digits, underscores, dots). */
  private def validateIdentifier(id: String): Unit =
    require(
      id.matches("[a-zA-Z_][a-zA-Z0-9_.]*"),
      s"Invalid SQL identifier: '$id'"
    )

  /** Initialize the table via Flyway migration.
    *
    * @throws org.flywaydb.core.api.FlywayException
    *   if migration fails (fail-fast: schema must be correct before app starts)
    */
  def initTable(): Unit = {
    dataset.foreach { d =>
      validateIdentifier(d)
      log.info(s"Setting up dataset $d")
      withStatement { stmt =>
        try {
          stmt.executeUpdate(s"CREATE SCHEMA IF NOT EXISTS $d")
          log.debug(s"Setup dataset $d")
        } catch {
          case _: java.sql.SQLSyntaxErrorException => // suppress known errors
          case other: Throwable                    => log.error(other.getMessage)
        }
      }
    }
    migrate()
  }
}
