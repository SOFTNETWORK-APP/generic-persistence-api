package app.softnetwork.persistence

import akka.actor.typed.ActorRef
import org.json4s.Formats
import app.softnetwork.persistence.model.{Entity, Timestamped}

import scala.language.implicitConversions

/** Created by smanciot on 19/03/2018.
  */
package object message {

  /** Command objects * */
  trait Command

  /** a command which includes a reference to the actor identity to whom a response has to be sent
    *
    * @tparam R
    *   - type of command result
    */
  trait CommandWithReply[R <: CommandResult] extends Command {
    def replyTo: ActorRef[R]
  }

  /** a wrapper around a command and its reference to the actor identity to whom a response has to
    * be sent
    *
    * @tparam C
    *   - type of command
    * @tparam R
    *   - type of command result
    */
  trait CommandWrapper[C <: Command, R <: CommandResult] extends CommandWithReply[R] {
    def command: C
  }

  /** CommandWrapper companion object
    */
  object CommandWrapper {
    def apply[C <: Command, R <: CommandResult](aCommand: C, aReplyTo: ActorRef[R]): C =
      new CommandWrapper[C, R] {
        override val command: C = aCommand
        override val replyTo: ActorRef[R] = aReplyTo
      }.asInstanceOf[C]
  }

  /** Entity command * */

  /** a command that should be handled by a specific entity
    */
  trait EntityCommand extends Command with Entity {
    def id: String // TODO rename to uuid ?
  }

  /** allow a command to be handled by no specific entity
    */
  trait AllEntities extends EntityCommand { _: Command =>
    override val id: String = ALL_KEY
  }

  /** Event objects * */
  trait Event

  /** A particular event that is intended to be broadcast
    */
  trait BroadcastEvent extends Event {
    def externalUuid: String
  }

  /** Crud events * */
  trait CrudEvent extends Event

  trait Created[T <: Timestamped] extends CrudEvent {
    def document: T
  }

  trait Updated[T <: Timestamped] extends CrudEvent {
    def document: T
    def upsert: Boolean = true
  }

  trait Loaded[T <: Timestamped] extends CrudEvent {
    def document: T
  }

  trait Upserted extends CrudEvent {
    def uuid: String
    def data: String
  }

  trait UpsertedDecorator { _: Upserted =>
    import app.softnetwork.serialization._
    implicit def formats: Formats = commonFormats
    implicit def excludedFields: List[String] = defaultExcludedFields :+ "data"
    def asMap: Map[String, Any] = caseClass2Map(this)
    override final lazy val data: String = asMap // implicit conversion Map[String, Any] => String
  }

  trait Deleted extends CrudEvent {
    def uuid: String
  }

  /** Command result * */
  trait CommandResult

  @SerialVersionUID(0L)
  class ErrorMessage(val message: String) extends CommandResult

  case object UnknownCommand extends ErrorMessage("UnknownCommand")

  case object UnknownEvent extends ErrorMessage("UnknownEvent")

  /** Count command result * */
  case class CountResponse(field: String, count: Int, error: Option[String] = None)

  @SerialVersionUID(0L)
  abstract class CountResult(results: Seq[CountResponse]) extends CommandResult

  /** Protobuf events Marker trait for serializing an event using Protobuf Serializer
    */
  trait ProtobufEvent extends Event

  /** Cbor events Marker trait for serializing an event using Jackson CBOR Serializer
    */
  trait CborEvent extends Event

  /** Correlation/audit capability shared by commands and events (Story 13.7 — cross-service audit
    * trail). `correlationId` is abstract so each side backs it with the storage matching its
    * lifecycle:
    *   - commands are plain case classes, transient, (Kryo-)serialized only in transit → a mutable
    *     `var` (no constructor churn; set once before dispatch).
    *   - events are immutable, journaled (ScalaPB) → backed by the generated proto `correlation_id`
    *     field, the durable hop that survives the journal + replay.
    */
  trait Auditable {

    /** ABSTRACT — backed by a `var` (commands) or a generated proto field (events). */
    def correlationId: Option[String]

    /** True once a correlation id has been set/propagated. */
    def auditable: Boolean = correlationId.nonEmpty

    /** Returns a value carrying `correlationId` — in place for commands (`this`), or an immutable
      * copy for ScalaPB events (the generated builder).
      */
    def withCorrelationId(correlationId: String): Auditable
  }

  /** Commands: the `var` adds NO constructor parameter to the case classes mixing it in, and
    * `withCorrelationId` mutates in place + returns `this`, so the caller keeps the concrete
    * command type for `!?`. Carried across the cluster-sharding boundary by the (chill/Kryo)
    * FieldSerializer.
    */
  trait AuditableCommand extends Command with Auditable {

    type T <: AuditableCommand

    var correlationId: Option[String] = None

    override def withCorrelationId(correlationId: String): T = {
      this.correlationId = Some(correlationId)
      this.asInstanceOf[T]
    }
  }

  /** Events: marker only — `correlationId` / `withCorrelationId` are SUPPLIED by ScalaPB from the
    * `optional string correlation_id` field (wired via `option (scalapb.message).extends`), so the
    * durable value lives in the immutable message (survives journal + replay), not in a `var`.
    */
  trait AuditableEvent extends Event with Auditable
}
