package app.softnetwork.elastic.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.{ActorSystem, ActorRef}
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.message._

import app.softnetwork.persistence.typed._
import app.softnetwork.serialization._

import akka.persistence.typed.scaladsl.Effect
import com.typesafe.scalalogging.StrictLogging

import app.softnetwork.persistence.typed.EntityBehavior

import app.softnetwork.elastic.client.ElasticClientApi

import app.softnetwork.elastic.message._

import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.elastic.persistence.query.ElasticProvider

import org.softnetwork.elastic.message.{DocumentUpsertedEvent, ElasticEvent}

import scala.language.implicitConversions
import scala.language.postfixOps

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 16/05/2020.
  */
trait ElasticBehavior[S  <: Timestamped] extends EntityBehavior[ElasticCommand, S, ElasticEvent, ElasticResult] 
  with ManifestWrapper[S] with ElasticProvider[S] with StrictLogging {_: ElasticClientApi =>

  private[this] val defaultAtMost = 10.second

  override def init(system: ActorSystem[_])(implicit tTag: ClassTag[ElasticCommand]): Unit = {
    logger.info(s"Initializing ${TypeKey.name}")
    super.init(system)
    initIndex()
  }

  /**
    *
    * @param entityId - entity identity
    * @param state - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @return effect
    */
  override def handleCommand(
                              entityId: String,
                              state: Option[S],
                              command: ElasticCommand,
                              replyTo: Option[ActorRef[ElasticResult]],
                              timers: TimerScheduler[ElasticCommand])(implicit context: ActorContext[ElasticCommand]
  ): Effect[ElasticEvent, Option[S]] = {
    command match {

      case cmd: CreateDocument[S] =>
        import cmd._
        implicit val m = manifestWrapper.wrapped
        Effect.persist[ElasticEvent, Option[S]](DocumentCreatedEvent(document))
          .thenRun(state => DocumentCreated(document.uuid) ~> replyTo)

      case cmd: UpdateDocument[S] =>
        import cmd._
        implicit val m = manifestWrapper.wrapped
        Effect.persist[ElasticEvent, Option[S]](DocumentUpdatedEvent(document, upsert))
          .thenRun(state => DocumentUpdated(document.uuid) ~> replyTo)

      case cmd: UpsertDocument =>
        import cmd._
        Effect.persist[ElasticEvent, Option[S]](
          DocumentUpsertedEvent(
            id,
            data
          )
        ).thenRun(state => DocumentUpserted(entityId) ~> replyTo)

      case cmd: DeleteDocument =>
        import cmd._
        Effect.persist[ElasticEvent, Option[S]](DocumentDeletedEvent(id))
          .thenRun(state => DocumentDeleted ~> replyTo).thenStop()

      case cmd: LoadDocument =>
        implicit val m = manifestWrapper.wrapped
        state match {
          case Some(s) => Effect.none.thenRun(state => DocumentLoaded(s) ~> replyTo)
          case _       => Effect.none.thenRun(state => DocumentNotFound ~> replyTo)
        }

      case cmd: LoadDocumentAsync =>
        implicit val m = manifestWrapper.wrapped
        state match {
          case Some(s) => Effect.none.thenRun(state => DocumentLoaded(s) ~> replyTo)
          case _       => Effect.none.thenRun(state => DocumentNotFound ~> replyTo)
        }

      case cmd: LookupDocuments =>
        import cmd._
        implicit val m = manifestWrapper.wrapped
        Try(search[S](sqlQuery)) match {
          case Success(documents) =>
            documents match {
              case Nil => Effect.none.thenRun(state => NoResultsFound ~> replyTo)
              case _   => Effect.none.thenRun(state => DocumentsFound[S](documents) ~> replyTo)
            }
          case Failure(f) =>
            context.log.error(f.getMessage, f)
            Effect.none.thenRun(state => NoResultsFound ~> replyTo)
        }

      case cmd: Count =>
        import cmd._
        implicit val ec = context.system.executionContext
        Effect.none.thenRun(state =>
          (countAsync(sqlQuery) complete() match {
            case Success(s) => ElasticCountResult(s)
            case Failure(f) =>
              logger.error(f.getMessage, f.fillInStackTrace())
              CountFailure
          }) ~> replyTo
        )

      case cmd: BulkUpdateDocuments =>
        import cmd._
        import app.softnetwork.elastic.client._
        implicit val bulkOptions = BulkOptions(index, `type`)
        Try(
          bulk[Map[String, Any]](
            documents.iterator,
            item => serialization.write(item),
            idKey = Some("uuid"),
            update = Some(true),
            delete = Some(false)
          )(bulkOptions, context.system)
        ) match {
          case Success(_) => Effect.none.thenRun(state => DocumentsBulkUpdated ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(state => BulkUpdateDocumentsFailure ~> replyTo)
        }

      case cmd: BulkDeleteDocuments =>
        import cmd._
        import app.softnetwork.elastic.client._
        implicit val bulkOptions = BulkOptions(index, `type`)
        Try(
          bulk[Map[String, Any]](
            documents.iterator,
            item => serialization.write(item),
            idKey = Some("uuid"),
            update = Some(false),
            delete = Some(true)
          )(bulkOptions, context.system)
        ) match {
          case Success(_) => Effect.none.thenRun(state => DocumentsBulkDeleted ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(state => BulkDeleteDocumentsFailure ~> replyTo)
        }

      case cmd: RefreshIndex =>
        Try(refresh(cmd.index.getOrElse(index))) match {
          case Success(_) => Effect.none.thenRun(state => IndexRefreshed ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(state => RefreshIndexFailure ~> replyTo)
        }

      case cmd: FlushIndex =>
        Try(flush(cmd.index.getOrElse(index))) match {
          case Success(_) => Effect.none.thenRun(state => IndexFlushed ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(_ => FlushIndexFailure ~> replyTo)
        }

      case _ => Effect.none.thenRun(_ => ElasticUnknownCommand ~> replyTo)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[S], event: ElasticEvent)(
    implicit context: ActorContext[ElasticCommand]): Option[S] = {
    event match {
      case evt: CrudEvent => handleElasticCrudEvent(state, evt)
      case _ => super.handleEvent(state, event)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - elastic event to hanlde
    * @return new state
    */
  private[this] def handleElasticCrudEvent(state: Option[S], event: CrudEvent)(
    implicit context: ActorContext[ElasticCommand]): Option[S] = {
    implicit val m = manifestWrapper.wrapped
    event match {
      case e: Created[S] =>
        import e._
        if(createDocument(document)){
          Some(document)
        }
        else{
          state
        }

      case e: Updated[S] =>
        import e._
        if(updateDocument(document, upsert)){
          Some(document)
        }
        else{
          state
        }

      case e: Upserted =>
        import e._
        if(upsertDocument(uuid, data)){
          loadDocument(uuid)
        }
        else{
          state
        }

      case e: Deleted =>
        import e._
        if(deleteDocument(uuid)){
          emptyState
        }
        else{
          state
        }

      case _ => state
    }
  }
}
