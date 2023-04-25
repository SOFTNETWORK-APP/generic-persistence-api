package app.softnetwork.elastic.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.ShardingEnvelope
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.typed._
import app.softnetwork.serialization._
import akka.persistence.typed.scaladsl.Effect
import com.typesafe.scalalogging.StrictLogging
import app.softnetwork.elastic.client.ElasticClientApi
import app.softnetwork.elastic.message._
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.elastic.persistence.query.ElasticProvider
import org.softnetwork.elastic.message.{DocumentUpsertedEvent, ElasticEvent}

import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** Created by smanciot on 16/05/2020.
  */
trait ElasticBehavior[S <: Timestamped]
    extends EntityBehavior[ElasticCommand, S, ElasticEvent, ElasticResult]
    with ManifestWrapper[S]
    with ElasticProvider[S]
    with StrictLogging { _: ElasticClientApi =>

  override def init(system: ActorSystem[_], maybeRole: Option[String] = None)(implicit
    tTag: ClassTag[ElasticCommand]
  ): ActorRef[ShardingEnvelope[ElasticCommand]] = {
    logger.info(s"Initializing ${TypeKey.name}")
    initIndex()
    super.init(system, maybeRole)
  }

  /** @param entityId
    *   - entity identity
    * @param state
    *   - current state
    * @param command
    *   - command to handle
    * @param replyTo
    *   - optional actor to reply to
    * @return
    *   effect
    */
  override def handleCommand(
    entityId: String,
    state: Option[S],
    command: ElasticCommand,
    replyTo: Option[ActorRef[ElasticResult]],
    timers: TimerScheduler[ElasticCommand]
  )(implicit context: ActorContext[ElasticCommand]): Effect[ElasticEvent, Option[S]] = {
    command match {

      case cmd: CreateDocument[S] =>
        import cmd._
        implicit val m: Manifest[S] = manifestWrapper.wrapped
        Effect
          .persist[ElasticEvent, Option[S]](DocumentCreatedEvent(document))
          .thenRun(_ => DocumentCreated(document.uuid) ~> replyTo)

      case cmd: UpdateDocument[S] =>
        import cmd._
        implicit val m: Manifest[S] = manifestWrapper.wrapped
        Effect
          .persist[ElasticEvent, Option[S]](DocumentUpdatedEvent(document, upsert))
          .thenRun(_ => DocumentUpdated(document.uuid) ~> replyTo)

      case cmd: UpsertDocument =>
        import cmd._
        Effect
          .persist[ElasticEvent, Option[S]](
            DocumentUpsertedEvent(
              id,
              data
            )
          )
          .thenRun(_ => DocumentUpserted(entityId) ~> replyTo)

      case cmd: DeleteDocument =>
        import cmd._
        Effect
          .persist[ElasticEvent, Option[S]](DocumentDeletedEvent(id))
          .thenRun(_ => DocumentDeleted ~> replyTo)
          .thenStop()

      case _: LoadDocument =>
        implicit val m: Manifest[S] = manifestWrapper.wrapped
        state match {
          case Some(s) => Effect.none.thenRun(_ => DocumentLoaded(s) ~> replyTo)
          case _       => Effect.none.thenRun(_ => DocumentNotFound ~> replyTo)
        }

      case _: LoadDocumentAsync =>
        implicit val m: Manifest[S] = manifestWrapper.wrapped
        state match {
          case Some(s) => Effect.none.thenRun(_ => DocumentLoaded(s) ~> replyTo)
          case _       => Effect.none.thenRun(_ => DocumentNotFound ~> replyTo)
        }

      case cmd: LookupDocuments =>
        import cmd._
        implicit val m: Manifest[S] = manifestWrapper.wrapped
        Try(search[S](sqlQuery)) match {
          case Success(documents) =>
            documents match {
              case Nil => Effect.none.thenRun(_ => NoResultsFound ~> replyTo)
              case _   => Effect.none.thenRun(_ => DocumentsFound[S](documents) ~> replyTo)
            }
          case Failure(f) =>
            context.log.error(f.getMessage, f)
            Effect.none.thenRun(_ => NoResultsFound ~> replyTo)
        }

      case cmd: Count =>
        import cmd._
        implicit val ec: ExecutionContextExecutor = context.system.executionContext
        Effect.none.thenRun(_ =>
          (countAsync(sqlQuery) complete () match {
            case Success(s) => ElasticCountResult(s)
            case Failure(f) =>
              logger.error(f.getMessage, f.fillInStackTrace())
              CountFailure
          }) ~> replyTo
        )

      case cmd: BulkUpdateDocuments =>
        import cmd._
        import app.softnetwork.elastic.client._
        implicit val bulkOptions: BulkOptions = BulkOptions(index, `type`)
        Try(
          bulk[Map[String, Any]](
            documents.iterator,
            item => serialization.write(item),
            idKey = Some("uuid"),
            update = Some(true),
            delete = Some(false)
          )(bulkOptions, context.system)
        ) match {
          case Success(_) => Effect.none.thenRun(_ => DocumentsBulkUpdated ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(_ => BulkUpdateDocumentsFailure ~> replyTo)
        }

      case cmd: BulkDeleteDocuments =>
        import cmd._
        import app.softnetwork.elastic.client._
        implicit val bulkOptions: BulkOptions = BulkOptions(index, `type`)
        Try(
          bulk[Map[String, Any]](
            documents.iterator,
            item => serialization.write(item),
            idKey = Some("uuid"),
            update = Some(false),
            delete = Some(true)
          )(bulkOptions, context.system)
        ) match {
          case Success(_) => Effect.none.thenRun(_ => DocumentsBulkDeleted ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(_ => BulkDeleteDocumentsFailure ~> replyTo)
        }

      case cmd: RefreshIndex =>
        Try(refresh(cmd.index.getOrElse(index))) match {
          case Success(_) => Effect.none.thenRun(_ => IndexRefreshed ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(_ => RefreshIndexFailure ~> replyTo)
        }

      case cmd: FlushIndex =>
        Try(flush(cmd.index.getOrElse(index))) match {
          case Success(_) => Effect.none.thenRun(_ => IndexFlushed ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(_ => FlushIndexFailure ~> replyTo)
        }

      case _ => Effect.none.thenRun(_ => ElasticUnknownCommand ~> replyTo)
    }
  }

  /** @param state
    *   - current state
    * @param event
    *   - event to hanlde
    * @return
    *   new state
    */
  override def handleEvent(state: Option[S], event: ElasticEvent)(implicit
    context: ActorContext[_]
  ): Option[S] = {
    event match {
      case evt: CrudEvent => handleElasticCrudEvent(state, evt)
      case _              => super.handleEvent(state, event)
    }
  }

  /** @param state
    *   - current state
    * @param event
    *   - elastic event to hanlde
    * @return
    *   new state
    */
  private[this] def handleElasticCrudEvent(state: Option[S], event: CrudEvent)(implicit
    context: ActorContext[_]
  ): Option[S] = {
    implicit val m: Manifest[S] = manifestWrapper.wrapped
    event match {
      case e: Created[S] =>
        import e._
        if (createDocument(document)) {
          Some(document)
        } else {
          state
        }

      case e: Updated[S] =>
        import e._
        if (updateDocument(document, upsert)) {
          Some(document)
        } else {
          state
        }

      case e: Upserted =>
        import e._
        if (upsertDocument(uuid, data)) {
          loadDocument(uuid)
        } else {
          state
        }

      case e: Deleted =>
        import e._
        if (deleteDocument(uuid)) {
          emptyState
        } else {
          state
        }

      case _ => state
    }
  }
}
