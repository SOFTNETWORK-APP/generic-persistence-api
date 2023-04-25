package app.softnetwork.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

import akka.persistence.typed._
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, RetentionCriteria}
import app.softnetwork.concurrent.Completion

import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.{Entity => _, _}
import app.softnetwork.persistence._

import scala.concurrent.duration._

import scala.language.implicitConversions
import scala.reflect.ClassTag

/** Created by smanciot on 16/05/2020.
  */
trait CommandTypeKey[C <: Command] {

  /** @param c
    *   - The type of commands to be send to this type of entity
    * @return
    *   A key that uniquely identifies the type of entity in the cluster
    */
  def TypeKey(implicit c: ClassTag[C]): EntityTypeKey[C]
}

trait EntityCommandHandler[C <: Command, S <: State, E <: Event, R <: CommandResult] {

  /** @param entityId
    *   - entity identity
    * @param state
    *   - current state
    * @param command
    *   - command to handle
    * @param replyTo
    *   - optional actor to reply to
    * @param timers
    *   - scheduled messages associated with this entity behavior
    * @return
    *   effect
    */
  def apply(
    entityId: String,
    state: Option[S],
    command: C,
    replyTo: Option[ActorRef[R]],
    timers: TimerScheduler[C]
  )(implicit
    context: ActorContext[C]
  ): Effect[E, Option[S]]
}

trait EntityEventHandler[S <: State, E <: Event] {

  /** @param state
    *   - current state
    * @param event
    *   - event to hanlde
    * @return
    *   new state
    */
  def apply(state: Option[S], event: E)(implicit context: ActorContext[_]): Option[S]
}

trait EntityBehavior[C <: Command, S <: State, E <: Event, R <: CommandResult]
    extends CommandTypeKey[C]
    with model.Entity
    with Completion {
  type W = CommandWrapper[C, R] with C

  type WR = CommandWithReply[R] with C

  /** @return
    *   number of events before saving a snapshot of the current actor entity state. If multiple
    *   events are persisted with a single Effect the snapshot will happen after all of the events
    *   are persisted rather than precisely every `numberOfEvents`
    */
  def snapshotInterval: Int = 10

  /** @return
    *   number of snapshots to keep
    */
  def numberOfSnapshots: Int = 2

  /** @return
    *   the key used to define the Entity Type Key of this actor that uniquely identifies the type
    *   of entity in this cluster and is then used to retrieve an EntityRef for a given entity
    *   identifier
    */
  def persistenceId: String

  /** @return
    *   node role required to start this entity actor
    */
  def role: String = ""

  final def TypeKey(implicit c: ClassTag[C]): EntityTypeKey[C] =
    EntityTypeKey[C](s"$persistenceId-$environment")

  /** @return
    *   the intial state for the entity before any events have been processed
    */
  val emptyState: Option[S] = None

  /** @param system
    *   - actor system
    * @param maybeRole
    *   - an optional node role required to start this entity
    * @param c
    * -
    * @return
    */
  def init(system: ActorSystem[_], maybeRole: Option[String] = None)(implicit
    c: ClassTag[C]
  ): Unit = {
    ClusterSharding(system) init Entity(TypeKey) { entityContext =>
      this(
        entityContext.entityId,
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
      )
    }.withRole(maybeRole.getOrElse(role))
  }

  type BE = E with BroadcastEvent

  /** @param event
    *   - the source event to broadcast
    * @return
    *   the list of events to broadcast which must include at least the source event
    */
  def broadcastEvent(event: BE): List[E] = List(event)

  /** associate a set of tags to an event before the latter will be appended to the event log
    *
    * This allows events to be easily filtered and queried based on their tags, improving the
    * efficiency of read-side projections
    *
    * @param entityId
    *   - entity id
    * @param event
    *   - the event to tag
    * @return
    *   set of tags to associate to this event
    */
  protected def tagEvent(entityId: String, event: E): Set[String] = Set.empty

  /** Set platform event tags, which will be used in persistence query
    *
    * @param entityId
    *   - entity id
    * @param event
    *   - the event to tag
    * @return
    *   platform event tags
    */
  private[this] def platformTagEvent(entityId: String, event: E): Set[String] =
    tagEvent(entityId, event).map(_ + s"-$environment")

  /** @param system
    *   - actor system
    * @param subscriber
    *   - the `self` actor
    * @param c
    *   - class tag of the commands supported by this actor
    */
  protected def subscribe(system: ActorSystem[_], subscriber: ActorRef[C])(implicit
    c: ClassTag[C]
  ): Unit = {}

  type CR = (C, Option[ActorRef[R]])

  private[this] val cr: PartialFunction[C, CR] = {
    case w: W   => (w.command, Some(w.replyTo))
    case wr: WR => (wr, Some(wr.replyTo))
    case c      => (c, None)
  }

  sealed trait DefaultEntityCommandHandler extends EntityCommandHandler[C, S, E, R] {

    /** @param entityId
      *   - entity identity
      * @param state
      *   - current state
      * @param command
      *   - command to handle
      * @param replyTo
      *   - optional actor to reply to
      * @param timers
      *   - scheduled messages associated with this entity behavior
      * @return
      *   effect
      */
    def apply(
      entityId: String,
      state: Option[S],
      command: C,
      replyTo: Option[ActorRef[R]],
      timers: TimerScheduler[C]
    )(implicit context: ActorContext[C]): Effect[E, Option[S]] = {
      handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  def entityCommandHandler: PartialFunction[C, EntityCommandHandler[C, S, E, R]] =
    defaultEntityCommandHandler

  protected final val defaultEntityCommandHandler
    : PartialFunction[C, EntityCommandHandler[C, S, E, R]] = { case _ =>
    new DefaultEntityCommandHandler {}
  }

  sealed trait DefaultEntityEventHandler extends EntityEventHandler[S, E] {

    /** @param state
      *   - current state
      * @param event
      *   - event to hanlde
      * @return
      *   new state
      */
    override def apply(state: Option[S], event: E)(implicit context: ActorContext[_]): Option[S] = {
      handleEvent(state, event)
    }
  }

  def entityEventHandler: PartialFunction[E, EntityEventHandler[S, E]] = defaultEntityEventHandler

  protected final val defaultEntityEventHandler: PartialFunction[E, EntityEventHandler[S, E]] = {
    case _ => new DefaultEntityEventHandler {}
  }

  final def apply(entityId: String, persistenceId: PersistenceId)(implicit
    c: ClassTag[C]
  ): Behavior[C] = {
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
            entityEventHandler(event)(state, event)(context)
          }
        )
          .onPersistFailure(
            SupervisorStrategy.restartWithBackoff(
              minBackoff = 2.seconds,
              maxBackoff = 20.seconds,
              randomFactor = 0.1
            )
          )
          /* Persistent actors can save snapshots of internal state every N events or when a given predicate of the state
          is fulfilled (snapshotWhen). */
          .withRetention(
            RetentionCriteria
              .snapshotEvery(
                numberOfEvents = snapshotInterval,
                keepNSnapshots = numberOfSnapshots
              )
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
            case (_, _: SnapshotCompleted) =>
              context.log.info(s"Snapshot completed for ${TypeKey.name} $entityId")
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

  /** @param entityId
    *   - entity identity
    * @param state
    *   - current state
    * @param command
    *   - command to handle
    * @param replyTo
    *   - optional actor to reply to
    * @param timers
    *   - scheduled messages associated with this entity behavior
    * @return
    *   effect
    */
  def handleCommand(
    entityId: String,
    state: Option[S],
    command: C,
    replyTo: Option[ActorRef[R]],
    timers: TimerScheduler[C]
  )(implicit context: ActorContext[C]): Effect[E, Option[S]] =
    command match {
      case _ => Effect.unhandled
    }

  /** This method is invoked whenever an event has been persisted successfully or when the entity is
    * started up to recover its state from the stored events
    *
    * @param state
    *   - current state
    * @param event
    *   - event to hanlde
    * @return
    *   new state created by applying the event to the previous state
    */
  def handleEvent(state: Option[S], event: E)(implicit context: ActorContext[_]): Option[S] =
    event match {
      case _ => state
    }

  /** This method is called just after the state of the corresponding entity has been successfully
    * recovered
    *
    * @param state
    *   - current state
    * @param context
    *   - actor context
    */
  def postRecoveryCompleted(state: Option[S])(implicit context: ActorContext[C]): Unit = {}
}

trait TimeStampedBehavior[C <: Command, S <: Timestamped, E <: Event, R <: CommandResult]
    extends EntityBehavior[C, S, E, R]
    with ManifestWrapper[S] {

  override def persistenceId: String = manifestWrapper.wrapped.runtimeClass.getSimpleName

  /** @param state
    *   - current state
    * @param event
    *   - event to hanlde
    * @return
    *   new state
    */
  override def handleEvent(state: Option[S], event: E)(implicit
    context: ActorContext[_]
  ): Option[S] = {
    import context._
    event match {
      case evt: Created[S] =>
        import evt._
        Some(document)

      case evt: Updated[S] =>
        import evt._
        Some(document)

      case _: Deleted => emptyState

      case _ =>
        log.warn(s"event $event not handled by $persistenceId")
        super.handleEvent(state, event)
    }
  }
}
