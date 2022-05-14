package app.softnetwork.resource.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.resource.message.ResourceEvents._
import app.softnetwork.resource.message.ResourceMessages._
import app.softnetwork.resource.model.Resource

import scala.language.implicitConversions
import app.softnetwork.persistence.{ManifestWrapper, now}
import app.softnetwork.persistence.typed._
import app.softnetwork.utils.{Base64Tools, HashTools}
import org.apache.tika.Tika

import java.io.ByteArrayInputStream
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 30/04/2020.
  */
sealed trait ResourceBehavior extends TimeStampedBehavior[ResourceCommand, Resource, ResourceEvent, ResourceResult] with ManifestWrapper[Resource]{

  override protected val manifestWrapper: ManifestW = ManifestW()

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event    - the event to tag
    * @return event tags
    */
  override protected def tagEvent(entityId: String, event: ResourceEvent): Set[String] = {
    event match {
      case _ => Set(
        persistenceId,
        s"${persistenceId.toLowerCase}-to-localfilesystem",
        s"${persistenceId.toLowerCase}-to-s3",
        s"${persistenceId.toLowerCase}-to-gcs"
      )
    }
  }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @return effect
    */
  override def handleCommand(entityId: String, state: Option[Resource], command: ResourceCommand,
                             replyTo: Option[ActorRef[ResourceResult]], timers: TimerScheduler[ResourceCommand])(
    implicit context: ActorContext[ResourceCommand]
  ): Effect[ResourceEvent, Option[Resource]] =
    command match {

      case cmd: CreateResource =>
        import cmd._
        val createdDate = now()
        Effect.persist(
          ResourceCreatedEvent(
            asResource(uuid, bytes)
              .withCreatedDate(createdDate)
              .withLastUpdated(createdDate)
          )
        ).thenRun(_ => {ResourceCreated ~> replyTo})

      case cmd: UpdateResource =>
        import cmd._
        val lastUpdated = now()
        val createdDate = {
          state match {
            case Some(resource) => resource.createdDate
            case None => now()
          }
        }
        Effect.persist(
          ResourceUpdatedEvent(
            asResource(uuid, bytes)
              .withCreatedDate(createdDate)
              .withLastUpdated(lastUpdated)
          )
        ).thenRun(_ => {ResourceUpdated ~> replyTo})

      case _: LoadResource =>
        state match {
          case Some(resource) => Effect.none.thenRun(_ => ResourceLoaded(resource) ~> replyTo)
          case _ => Effect.none.thenRun(_ => ResourceNotFound ~> replyTo)
        }

      case _: DeleteResource =>
        Effect.persist[ResourceEvent, Option[Resource]](
          ResourceDeletedEvent(
            entityId
          )
        ).thenRun(_ => {
          ResourceDeleted ~> replyTo
        }).thenStop()

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }

  private[this] def asResource(uuid: String, bytes: Array[Byte]) : Resource = {
    val mimetype =
      Try(new Tika().detect(bytes)) match {
        case Success(s) => Some(s)
        case Failure(_) => None
      }
    val content = Base64Tools.encodeBase64(bytes)
    val md5 = HashTools.hashStream(
      new ByteArrayInputStream(
        bytes
      )
    ).getOrElse("")
    Resource.defaultInstance
      .withUuid(uuid)
      .withContent(content)
      .withMd5(md5)
      .copy(mimetype = mimetype)
  }
}

object ResourceBehavior extends ResourceBehavior
