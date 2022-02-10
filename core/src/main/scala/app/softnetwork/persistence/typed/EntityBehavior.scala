package app.softnetwork.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler, Behaviors}
import akka.actor.typed.{ActorSystem, SupervisorStrategy, Behavior, ActorRef}

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

import akka.persistence.typed._
import akka.persistence.typed.scaladsl.{Recovery, EventSourcedBehavior, Effect, RetentionCriteria}
import app.softnetwork.concurrent.Completion


import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.{Entity => InternalEntity, _}
import app.softnetwork.persistence._

import scala.concurrent.duration._

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Created by smanciot on 16/05/2020.
  */
trait CommandTypeKey[C <: Command] {
  def TypeKey(implicit c: ClassTag[C]): EntityTypeKey[C]
}

trait EntityCommandHandler[C <: Command, S <: State, E <: Event, R <: CommandResult] {
  /**
    *
    * @param entityId - entity identity
    * @param state - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @param timers - scheduled messages associated with this entity behavior
    * @return effect
    */
  def apply(entityId: String, state: Option[S], command: C, replyTo: Option[ActorRef[R]], timers: TimerScheduler[C])(
    implicit context: ActorContext[C]
  ): Effect[E, Option[S]]
}

trait EntityBehavior[C <: Command, S <: State, E <: Event, R <: CommandResult] extends CommandTypeKey[C]
  with InternalEntity
  with Completion {
  type W = CommandWrapper[C, R] with C

  type WR = CommandWithReply[R] with C

  /** number of events received before generating a snapshot - should be configurable **/
  def snapshotInterval: Int = 10

  def persistenceId: String

  final def TypeKey(implicit c: ClassTag[C]): EntityTypeKey[C] =
    EntityTypeKey[C](s"$persistenceId-$environment")

  val emptyState: Option[S] = None

  def init(system: ActorSystem[_])(implicit c: ClassTag[C]): Unit = {
    ClusterSharding(system)init Entity(TypeKey) { entityContext =>
      this(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
    }
  }

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event - the event to tag
    * @return event tags
    */
  protected def tagEvent(entityId: String, event: E): Set[String] = Set.empty

  /**
    *
    * Set platform event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event - the event to tag
    * @return platform event tags
    */
  private[this] def platformTagEvent(entityId: String, event: E): Set[String] =
    tagEvent(entityId, event).map(_ + s"-$environment")

  /**
    *
    * @param system - actor system
    * @param subscriber - the `self` actor
    * @param c - class tag of the commands supported by this actor
    */
  protected def subscribe(system: ActorSystem[_], subscriber: ActorRef[C])(implicit c: ClassTag[C]): Unit = {}

  implicit def resultToMaybeReply(r: R): MaybeReply = new MaybeReply {
    def apply(): Option[ActorRef[R]] => Unit = {
      case Some(subscriber) => subscriber ! r
      case _ =>
    }
  }

  sealed trait MaybeReply {
    def apply(): Option[ActorRef[R]] => Unit
    final def ~>(replyTo: Option[ActorRef[R]]): Unit = apply()(replyTo)
  }

  type CR = (C, Option[ActorRef[R]])

  private[this] val cr: PartialFunction[C, CR] = {
    case w: W => (w.command, Some(w.replyTo))
    case wr: WR => (wr, Some(wr.replyTo))
    case c => (c, None)
  }

  sealed trait DefaultEntityCommandHandler extends EntityCommandHandler[C, S, E, R] {
    /**
      *
      * @param entityId - entity identity
      * @param state - current state
      * @param command - command to handle
      * @param replyTo - optional actor to reply to
      * @param timers - scheduled messages associated with this entity behavior
      * @return effect
      */
    def apply(entityId: String, state: Option[S], command: C, replyTo: Option[ActorRef[R]], timers: TimerScheduler[C])(
      implicit context: ActorContext[C]): Effect[E, Option[S]] = {
      handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  def entityCommandHandler: PartialFunction[C, EntityCommandHandler[C, S, E, R]] = defaultEntityCommandHandler

  protected final val defaultEntityCommandHandler: PartialFunction[C, EntityCommandHandler[C, S, E, R]] = {
    case _ => new DefaultEntityCommandHandler {}
  }

  final def apply(entityId: String, persistenceId: PersistenceId)(implicit c: ClassTag[C]): Behavior[C] = {
    Behaviors.withTimers(timers => {
      Behaviors.setup { context =>
        context.log.info(s"Starting $persistenceId")
        subscribe(context.system, context.self)
        EventSourcedBehavior[C, E, Option[S]](
          persistenceId = persistenceId,
          emptyState = emptyState,
          commandHandler = { (state, command) =>
            context.log.debug(s"handling command $command for ${TypeKey.name} $entityId")
            val _cr = cr(command)
            entityCommandHandler(_cr._1)(entityId, state, _cr._1, _cr._2, timers)(context)
          },
          eventHandler = { (state, event) =>
            context.log.debug(s"handling event $event for ${TypeKey.name} $entityId")
            handleEvent(state, event)(context)
          }
        )
          .onPersistFailure(
            SupervisorStrategy.restartWithBackoff(minBackoff = 2.seconds, maxBackoff = 20.seconds, randomFactor = 0.1)
          )
          /* Persistent actors can save snapshots of internal state every N events or when a given predicate of the state
          is fulfilled (snapshotWhen). */
          .withRetention(
          RetentionCriteria.snapshotEvery(numberOfEvents = snapshotInterval, keepNSnapshots = 2)
            .withDeleteEventsOnSnapshot /* after a snapshot has been successfully stored, a delete of the events
          (journaled by a single event sourced actor) up until the sequence number of the data held by that snapshot
          can be issued */
        )
          /* During recovery, the persistent actor is using the latest saved snapshot to initialize the state.
          Thereafter the events after the snapshot are replayed using the event handler to recover the persistent actor
          to its current (i.e. latest) state. */
          .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.latest))
          .receiveSignal {
            case (_, f: RecoveryFailed) =>
              context.log.error(s"Recovery failed for ${TypeKey.name} $entityId", f.failure)
            case (state, _: RecoveryCompleted) =>
              context.log.info(s"Recovery completed for ${TypeKey.name} $entityId")
              postRecoveryCompleted(state)(context)
            case (_, _: SnapshotCompleted) => context.log.info(s"Snapshot completed for ${TypeKey.name} $entityId")
            case (_, f: SnapshotFailed) =>
              context.log.warn(s"Snapshot failed for ${TypeKey.name} $entityId", f.failure)
            case (_, f: DeleteSnapshotsFailed) =>
              context.log.warn(s"Snapshot deletion failed for ${TypeKey.name} $entityId", f.failure)
            case (_, f: DeleteEventsFailed) =>
              context.log.warn(s"Events deletion failed for ${TypeKey.name} $entityId", f.failure)
          }
          .withTagger(event => platformTagEvent(entityId, event))
      }
    })
  }

  /**
    *
    * @param entityId - entity identity
    * @param state - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @param timers - scheduled messages associated with this entity behavior
    * @return effect
    */
  def handleCommand(entityId: String, state: Option[S], command: C, replyTo: Option[ActorRef[R]], timers: TimerScheduler[C])(
    implicit context: ActorContext[C]): Effect[E, Option[S]] =
    command match {
      case _    => Effect.unhandled
    }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  def handleEvent(state: Option[S], event: E)(implicit context: ActorContext[C]): Option[S] =
    event match {
      case _  => state
    }

  /**
    *
    * @param state - current entity state
    * @param context - actor context
    */
  def postRecoveryCompleted(state: Option[S])(implicit context: ActorContext[C]): Unit  = {}
}
