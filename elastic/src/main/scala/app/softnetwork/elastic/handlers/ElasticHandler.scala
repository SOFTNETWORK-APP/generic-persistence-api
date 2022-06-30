package app.softnetwork.elastic.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.elastic.message
import app.softnetwork.elastic.sql.SQLQuery
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.elastic.message._
import app.softnetwork.persistence.model.Timestamped

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.existentials

/**
  * Created by smanciot on 02/05/2020.
  */
trait ElasticHandler[T <: Timestamped] extends EntityPattern[ElasticCommand, ElasticResult] {
  _: CommandTypeKey[ElasticCommand] =>
}

trait ElasticDao[T <: Timestamped] {_: ElasticHandler[T] =>

  def create(document: T)(implicit system: ActorSystem[_], m: Manifest[T]): Future[Either[ElasticResult, DocumentCreated]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? CreateDocument(document) map {
      case r: DocumentCreated => Right(r)
      case other => Left(other)
    }
  }

  def update(document: T)(implicit system: ActorSystem[_], m: Manifest[T]): Future[Either[ElasticResult, DocumentUpdated]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? UpdateDocument(document) map {
      case r: DocumentUpdated => Right(r)
      case other => Left(other)
    }
  }

  def upsert(id: String, data: Map[String, Any])(implicit system: ActorSystem[_]): Future[Either[ElasticResult, DocumentUpserted]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? UpsertDocument(id, data) map {
      case r: DocumentUpserted => Right(r)
      case other => Left(other)
    }
  }

  def delete(id: String)(implicit system: ActorSystem[_], m: Manifest[T]): Future[Either[ElasticResult, DocumentDeleted]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? DeleteDocument(id) map {
      case r: DocumentDeleted => Right(r)
      case other => Left(other)
    }
  }

  def load(id: String)(implicit system: ActorSystem[_]): Future[Either[ElasticResult, DocumentLoaded[_ <: Timestamped]]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? LoadDocument(id) map {
      case r: DocumentLoaded[_] => Right(r)
      case other => Left(other)
    }
  }

  def search(query: SQLQuery)(implicit system: ActorSystem[_]): Future[Either[ElasticResult, DocumentsFound[_ <: Timestamped]]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? LookupDocuments(query) map {
      case r: DocumentsFound[_] => Right(r)
      case other => Left(other)
    }
  }

  def count(query: SQLQuery)(implicit system: ActorSystem[_]): Future[Either[ElasticResult, ElasticResult with CountResult]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? Count(query) map {
      case r: CountResult => Right(r)
      case other => Left(other)
    }
  }

  def bulkUpdate(documents: List[Map[String, Any]])(implicit system: ActorSystem[_]): Future[Either[ElasticResult, message.DocumentsBulkUpdated.type]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? BulkUpdateDocuments(documents) map {
      case r: DocumentsBulkUpdated.type => Right(r)
      case other => Left(other)
    }
  }

  def bulkDelete(documents: List[Map[String, Any]])(implicit system: ActorSystem[_]): Future[Either[ElasticResult, message.DocumentsBulkDeleted.type]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? BulkDeleteDocuments(documents) map {
      case r: DocumentsBulkDeleted.type => Right(r)
      case other => Left(other)
    }
  }

  def flush(index: Option[String])(implicit system: ActorSystem[_]): Future[Either[ElasticResult, message.IndexFlushed.type]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? FlushIndex(index) map {
      case r: IndexFlushed.type => Right(r)
      case other => Left(other)
    }
  }

  def refresh(index: Option[String])(implicit system: ActorSystem[_]): Future[Either[ElasticResult, message.IndexRefreshed.type]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    this !? RefreshIndex(index) map {
      case r: IndexRefreshed.type => Right(r)
      case other => Left(other)
    }
  }

}
