package app.softnetwork.persistence.jdbc.query

import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence._
import app.softnetwork.persistence.jdbc.db.SlickDatabase
import app.softnetwork.persistence.model.{StateWrapper, Timestamped}
import app.softnetwork.persistence.query.JsonProvider
import app.softnetwork.serialization.serialization
import org.json4s.Formats
import slick.jdbc.JdbcProfile

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait JdbcStateProvider[T <: Timestamped]
    extends JsonProvider[T]
    with SlickDatabase
    with Completion {
  _: ManifestWrapper[T] with JdbcProfile =>

  def dataset: Option[String] = {
    if (config.hasPath("jdbc-external-processor.dataset")) {
      val d = config.getString("jdbc-external-processor.dataset")
      if (d.nonEmpty) {
        Some(d)
      } else {
        None
      }
    } else {
      None
    }
  }

  lazy val table: String = getType[T](manifestWrapper.wrapped)

  private[this] lazy val tableFullName: String = dataset match {
    case Some(s) => s"$s.$table"
    case _       => table
  }

  def excludedFields: Set[String] = Set("__serializedSizeMemoized")

  implicit def executionContext: ExecutionContext

  import api._

  /** Creates the underlying document to the database
    *
    * @param document
    *   - the document to create
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  override final def createDocument(document: T)(implicit
    t: ClassTag[T]
  ): Boolean = writeToDb(
    StateWrapper[T](
      document.uuid,
      document.lastUpdated,
      deleted = false,
      Option(document)
    )
  )

  /** Updates the underlying document to the database
    *
    * @param document
    *   - the document to update
    * @param upsert
    *   - whether or not to create the underlying document if it does not exist in the external
    *     system
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  override final def updateDocument(document: T, upsert: Boolean = true)(implicit
    t: ClassTag[T]
  ): Boolean = {
    var to_update: Boolean = false
    if (upsert) {
      loadDocument(document.uuid) match {
        case Some(_) =>
          to_update = true
        case _ =>
      }
    }
    writeToDb(
      StateWrapper[T](
        document.uuid,
        document.lastUpdated,
        deleted = false,
        Option(document)
      ),
      to_update = to_update
    )
  }

  /** Upsert the underlying document referenced by its uuid to the database
    *
    * @param uuid
    *   - the uuid of the document to upsert
    * @param data
    *   - the document data
    * @return
    *   whether the operation is successful or not
    */
  override def upsertDocument(uuid: String, data: String): Boolean = {
    (loadDocument(uuid) match {
      case Some(document: T) =>
        implicit val manifest: Manifest[T] = manifestWrapper.wrapped
        var state = serialization.read[Map[String, Any]](serialization.write(document))
        val updatedState = serialization.read[Map[String, Any]](data)
        for ((key, value) <- updatedState) {
          if (!excludedFields.contains(key)) {
            state = state + (key -> value)
          }
        }
        val updatedDocument = serialization.write(state)
        Try(serialization.read[T](updatedDocument)(formats, manifest)) match {
          case Success(updatedState: T) =>
            Some(updatedState)
          case Failure(e) =>
            log.error(s"Failed to update document $uuid with data $data", e)
            return false
        }
      case _ => None
    }) match {
      case Some(updatedDocument: T) =>
        updateDocument(updatedDocument)
      case _ =>
        writeToDb(
          StateWrapper[T](uuid, Instant.now, deleted = false, None),
          to_update = false,
          Some(data)
        )
    }
  }

  /** Deletes the underlying document referenced by its uuid to the database
    *
    * @param uuid
    *   - the uuid of the document to delete
    * @return
    *   whether the operation is successful or not
    */
  override final def deleteDocument(uuid: String): Boolean =
    loadDocument(uuid) match {
      case Some(document: T) =>
        writeToDb(
          StateWrapper[T](uuid, document.lastUpdated, deleted = true, Some(document)),
          to_update = true
        )
      case _ =>
        writeToDb(
          StateWrapper[T](uuid, Instant.now, deleted = true, None)
        )
    }

  /** Load the document referenced by its uuid
    *
    * @param uuid
    *   - the document uuid
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the document retrieved, None otherwise
    */
  override final def loadDocument(
    uuid: String
  )(implicit m: Manifest[T], formats: Formats): Option[T] = load(uuid)

  /** Search documents
    *
    * @param query
    *   - the search query
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the documents found or an empty list otherwise
    */
  override final def searchDocuments(
    query: String
  )(implicit m: Manifest[T], formats: Formats): List[T] = search(query)

  /** Write the document to the database
    *
    * @param document
    *   - the document to write
    * @param to_update
    *   - whether or not the document has been updated
    * @param data
    *   - optional data to write
    * @return
    *   whether the operation is successful or not
    */
  protected def writeToDb(
    document: StateWrapper[T],
    to_update: Boolean = false,
    data: Option[String] = None
  ): Boolean = {
    val state: String = data match {
      case Some(d) => d
      case None =>
        document.state match {
          case Some(s) => writeState(s)
          case _       => "{}"
        }
    }
    (if (!to_update) {
       insert(document.uuid, document.lastUpdated, document.deleted, state)
     } else {
       log.debug(s"Updating document ${document.uuid} with data $state")
       update(document.uuid, document.lastUpdated, document.deleted, state)
     }) && writeToFile(
      document,
      data
    ) // write to file as well
  }

  /** Creates the underlying document to the database
    *
    * @param uuid
    *   - the document uuid
    * @param lastUpdated
    *   - the last updated date
    * @param deleted
    *   - whether or not the document is deleted
    * @param state
    *   - the document state
    * @return
    *   whether the operation is successful or not
    */
  def insert(
    uuid: String,
    lastUpdated: Instant,
    deleted: Boolean,
    state: String
  ): Boolean = {
    val action =
      (states += (uuid, lastUpdated, deleted, state)).map(_ > 0)
    db.run(action) complete () match {
      case Success(value) =>
        log.debug(s"Insert to $tableFullName with $uuid -> $value")
        value
      case Failure(f) =>
        log.error(f.getMessage, f)
        false
    }
  }

  /** Updates the underlying document to the database
    *
    * @param uuid
    *   - the uuid of the document to update
    * @param lastUpdated
    *   - the last updated date of the document
    * @param deleted
    *   - whether or not the document is deleted
    * @param state
    *   - the state of the document
    * @return
    *   whether the operation is successful or not
    */
  def update(
    uuid: String,
    lastUpdated: Instant,
    deleted: Boolean,
    state: String
  ): Boolean = {
    val action = states
      .filter(_.uuid === uuid)
      .update(
        (uuid, lastUpdated, deleted, state)
      )
      .map(_ > 0)
    db.run(action) complete () match {
      case Success(value) =>
        if (deleted) {
          log.debug(s"Delete from $tableFullName with $uuid -> $value")
        } else {
          log.debug(s"Update to $tableFullName with $uuid -> $value")
        }
        value
      case Failure(f) =>
        log.error(f.getMessage, f)
        false
    }
  }

  /** Deletes the underlying document referenced by its uuid to the database
    *
    * @param uuid
    *   - the uuid of the document to delete
    * @return
    *   whether the operation is successful or not
    */
  def destroy(uuid: String): Boolean = {
    val action = states.filter(_.uuid === uuid).delete.map(_ > 0)
    db.run(action) complete () match {
      case Success(value) =>
        log.debug(s"Delete from $tableFullName with $uuid -> $value")
        value
      case Failure(f) =>
        log.error(f.getMessage, f)
        false
    }
  }

  /** Load the document referenced by its uuid
    *
    * @param uuid
    *   - the document uuid
    * @return
    *   the document retrieved, None otherwise
    */
  def load(uuid: String): Option[T] = {
    implicit val manifest: Manifest[T] = manifestWrapper.wrapped
    val action = states.filter(_.uuid === uuid).result.headOption
    db.run(action) complete () match {
      case Success(value) =>
        value match {
          case Some(document) =>
            //logger.info(s"$document")
            document match {
              case (_, _, deleted, _) if deleted =>
                log.debug(s"Load $tableFullName with $uuid -> None")
                None
              case (_, _, _, state) =>
                log.debug(s"Load $tableFullName with $uuid -> $value")
                Option(readState(state)(manifest))
            }
          case _ =>
            log.debug(s"Load $tableFullName with $uuid -> None")
            None
        }
      case Failure(f) =>
        log.error(f.getMessage, f)
        None
    }
  }

  /** Search documents
    *
    * @param query
    *   - the search query
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the documents found or an empty list otherwise
    */
  def search(
    query: String
  )(implicit m: Manifest[T], formats: Formats): List[T] = {
    val action: DBIOAction[List[String], NoStream, Effect] = {
      sql"""
        SELECT state FROM $tableFullName WHERE $query
      """.as[String].map(_.toList)
    }
    db.run(action) complete () match {
      case Success(value) =>
        log.debug(s"Search $tableFullName with $query -> $value")
        value.map(readState)
      case Failure(f) =>
        log.error(f.getMessage, f)
        Nil
    }
  }

  class States(tag: Tag) extends Table[(String, Instant, Boolean, String)](tag, dataset, table) {
    def uuid = column[String]("uuid", O.PrimaryKey)
    def lastUpdated = column[Instant]("last_updated")
    def deleted = column[Boolean]("deleted")
    def state = column[String]("state")

    def * = (uuid, lastUpdated, deleted, state)
  }

  lazy val states = TableQuery[States]

  protected def writeState(state: T): String = {
    serialization.write(state)(formats)
  }

  protected def readState(state: String)(implicit manifest: Manifest[T]): T = {
    serialization.read(state)(formats, manifest)
  }

  def initTable(): Unit = {
    initDataset()
    log.info(
      s"Setting up table $tableFullName ${states.schema.createStatements.mkString(";\n")}"
    )
    db.run(DBIO.seq(states.schema.createIfNotExists)).complete() match {
      case Success(_) =>
        log.debug(s"Setup table $tableFullName")
      case Failure(f) =>
        log.error(f.getMessage, f)
    }
  }

  def initDataset(): Unit = {
    dataset match {
      case Some(d) =>
        log.info(
          s"Setting up dataset $d"
        )
        val ddl = s"CREATE SCHEMA IF NOT EXISTS $d"
        withStatement { stmt =>
          try {
            stmt.executeUpdate(ddl)
            log.debug(s"Setup dataset $d")
          } catch {
            case t: java.sql.SQLSyntaxErrorException
                if t.getMessage contains "ORA-00942" => // suppress known error message in the test
            case other: Throwable => log.error(other.getMessage)
          }
        }
      case _ =>
    }
  }
}
