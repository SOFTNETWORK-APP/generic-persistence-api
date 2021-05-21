package app.softnetwork.elastic

import app.softnetwork.elastic.sql.SQLQuery
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Timestamped

import org.softnetwork.elastic.message._

/**
  * Created by smanciot on 01/07/2018.
  */
package object message {

  sealed trait ElasticCommand extends EntityCommand

  /** Entity Commands **/

  @SerialVersionUID(0L)
  case class LoadDocument(id: String) extends ElasticCommand

  @SerialVersionUID(0L)
  case class LoadDocumentAsync(id: String) extends ElasticCommand

  @SerialVersionUID(0L)
  case class LookupDocuments(sqlQuery: SQLQuery) extends ElasticCommand with AllEntities

  @SerialVersionUID(0L)
  case class LookupDocumentsAsync(sqlQuery: SQLQuery) extends ElasticCommand with AllEntities

  @SerialVersionUID(0L)
  case class DeleteDocument(id: String) extends ElasticCommand

  @SerialVersionUID(0L)
  case class DeleteDocumentAsync(id: String) extends ElasticCommand

  @SerialVersionUID(0L)
  case class UpsertDocument(id: String, data: String) extends ElasticCommand

  @SerialVersionUID(0L)
  case class BulkUpdateDocuments(documents: List[Map[String, Any]]) extends ElasticCommand with AllEntities

  @SerialVersionUID(0L)
  case class BulkDeleteDocuments(documents: List[Map[String, Any]]) extends ElasticCommand with AllEntities

  @SerialVersionUID(0L)
  case class RefreshIndex(index: Option[String]) extends ElasticCommand with AllEntities

  @SerialVersionUID(0L)
  case class FlushIndex(index: Option[String]) extends ElasticCommand with AllEntities

  @SerialVersionUID(0L)
  case class Count(sqlQuery: SQLQuery) extends ElasticCommand with AllEntities

  /** Crud Commands **/

  sealed trait ElasticCrudCommand[T <: Timestamped] extends ElasticCommand {
    val document: T
    override val id = document.uuid
  }

  @SerialVersionUID(0L)
  case class CreateDocument[T <: Timestamped:Manifest](document: T) extends ElasticCrudCommand[T]

  @SerialVersionUID(0L)
  case class CreateDocumentAsync[T <: Timestamped:Manifest](document: T) extends ElasticCrudCommand[T]

  @SerialVersionUID(0L)
  case class UpdateDocument[T <: Timestamped:Manifest](document: T, upsert: Boolean = true)
    extends ElasticCrudCommand[T]

  @SerialVersionUID(0L)
  case class UpdateDocumentAsync[T <: Timestamped:Manifest](document: T, upsert: Boolean = true)
    extends ElasticCrudCommand[T]

  /** Crud events **/
  case class DocumentCreatedEvent[T <: Timestamped: Manifest](document: T) extends Created[T] with ElasticEvent

  case class DocumentUpdatedEvent[T <: Timestamped: Manifest](document: T, override val upsert: Boolean)
    extends Updated[T] with ElasticEvent

  case class DocumentDeletedEvent(uuid: String) extends Deleted with ElasticEvent

  /** Command results **/

  trait ElasticResult extends CommandResult

  @SerialVersionUID(0L)
  case class DocumentLoaded[T <: Timestamped:Manifest](document: T) extends ElasticResult

  @SerialVersionUID(0L)
  case class DocumentsFound[T <: Timestamped:Manifest](documents: List[T]) extends ElasticResult

  @SerialVersionUID(0L)
  case class DocumentCreated(id: String) extends ElasticResult

  @SerialVersionUID(0L)
  case class DocumentUpdated(id: String) extends ElasticResult

  @SerialVersionUID(0L)
  case class DocumentUpserted(id: String) extends ElasticResult

  @SerialVersionUID(0L)
  case class DocumentDeleted(id: String) extends ElasticResult

  @SerialVersionUID(0L)
  case object DocumentsBulkUpdated extends ElasticResult

  @SerialVersionUID(0L)
  case object DocumentsBulkDeleted extends ElasticResult

  @SerialVersionUID(0L)
  case class ElasticCountResult(results: Seq[CountResponse]) extends CountResult(results) with ElasticResult

  @SerialVersionUID(0L)
  case object DocumentDeleted extends ElasticResult

  @SerialVersionUID(0L)
  case object IndexRefreshed extends ElasticResult

  @SerialVersionUID(0L)
  case object IndexFlushed extends ElasticResult

  class ElasticError(override val message: String) extends ErrorMessage(message) with ElasticResult

  @SerialVersionUID(0L)
  case object ElasticUnknownCommand extends ElasticError("ElasticUnknownCommand")

  @SerialVersionUID(0L)
  case object ElasticUnknownEvent extends ElasticError("ElasticUnknownEvent")

  @SerialVersionUID(0L)
  case object DocumentNotFound extends ElasticError("DocumentNotFound") // TODO add document type and uuid

  @SerialVersionUID(0L)
  case object DocumentNotCreated extends ElasticError("DocumentNotCreated")

  @SerialVersionUID(0L)
  case object DocumentNotUpdated extends ElasticError("DocumentNotUpdated")

  @SerialVersionUID(0L)
  case object DocumentNotUpserted extends ElasticError("DocumentNotUpserted")

  @SerialVersionUID(0L)
  case object DocumentNotDeleted extends ElasticError("DocumentNotDeleted")

  @SerialVersionUID(0L)
  case object NoResultsFound extends ElasticError("NoResultsFound")

  @SerialVersionUID(0L)
  case object CountFailure extends ElasticError("CountFailure")

  @SerialVersionUID(0L)
  case object BulkUpdateDocumentsFailure extends ElasticError("BulkUpdateDocumentsFailure")

  @SerialVersionUID(0L)
  case object BulkDeleteDocumentsFailure extends ElasticError("BulkDeleteDocumentsFailure")

  @SerialVersionUID(0L)
  case object RefreshIndexFailure extends ElasticError("RefreshIndexFailure")

  @SerialVersionUID(0L)
  case object FlushIndexFailure extends ElasticError("FlushIndexFailure")
}
