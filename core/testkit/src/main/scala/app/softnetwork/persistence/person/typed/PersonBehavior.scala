package app.softnetwork.persistence.person.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.persistence
import app.softnetwork.persistence.person.message.{
  AddPerson,
  DeletePerson,
  LoadPerson,
  NameUpdated,
  NameUpdatedEvent,
  PersonAdded,
  PersonCommand,
  PersonCommandResult,
  PersonCreatedEvent,
  PersonDeleted,
  PersonDeletedEvent,
  PersonEvent,
  PersonLoaded,
  PersonNotFound,
  PersonUpdated,
  PersonUpdatedEvent,
  UpdateName,
  UpdatePerson
}
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.typed._
import app.softnetwork.time._

trait PersonBehavior
    extends TimeStampedBehavior[PersonCommand, Person, PersonEvent, PersonCommandResult] {

  override protected val manifestWrapper: ManifestW = ManifestW()

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
  override protected def tagEvent(entityId: String, event: PersonEvent): Set[String] = {
    Set(s"${persistenceId.toLowerCase}-to-external", persistenceId)
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
  override def handleCommand(
    entityId: String,
    state: Option[Person],
    command: PersonCommand,
    replyTo: Option[ActorRef[PersonCommandResult]],
    timers: TimerScheduler[PersonCommand]
  )(implicit context: ActorContext[PersonCommand]): Effect[PersonEvent, Option[Person]] = {
    command match {
      case cmd: AddPerson =>
        import cmd._
        Effect
          .persist(PersonCreatedEvent(Person(entityId, name, birthDate)))
          .thenRun(_ => PersonAdded ~> replyTo)
      case LoadPerson =>
        state match {
          case Some(person) => Effect.none.thenRun(_ => PersonLoaded(person) ~> replyTo)
          case _ =>
            Effect.none.thenRun(_ => PersonNotFound ~> replyTo)
        }
      case cmd: UpdatePerson =>
        state match {
          case Some(person) =>
            import cmd._
            Effect
              .persist(
                PersonUpdatedEvent(
                  person.copy(name = name, birthDate = birthDate, lastUpdated = persistence.now())
                )
              )
              .thenRun(_ => PersonUpdated ~> replyTo)
          case _ =>
            Effect.none.thenRun(_ => PersonNotFound ~> replyTo)
        }
      case cmd: UpdateName =>
        state match {
          case Some(_) =>
            import cmd._
            Effect
              .persist(NameUpdatedEvent(entityId, name, persistence.now()))
              .thenRun(_ => NameUpdated ~> replyTo)
          case _ =>
            Effect.none.thenRun(_ => PersonNotFound ~> replyTo)
        }
      case DeletePerson =>
        state match {
          case Some(_) =>
            Effect
              .persist(PersonDeletedEvent(entityId))
              .thenRun(_ => PersonDeleted ~> replyTo)
          case _ =>
            Effect.none.thenRun(_ => PersonNotFound ~> replyTo)
        }
      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
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
  override def handleEvent(state: Option[Person], event: PersonEvent)(implicit
    context: ActorContext[_]
  ): Option[Person] = {
    event match {
      case evt: NameUpdatedEvent =>
        state.map(_.copy(name = evt.name, lastUpdated = evt.lastUpdated))
      case _ =>
        super.handleEvent(
          state,
          event
        ) // Created, Updated and Deleted events are handled by TimeStampedBehavior event handler
    }
  }
}

object PersonBehavior extends PersonBehavior {}
