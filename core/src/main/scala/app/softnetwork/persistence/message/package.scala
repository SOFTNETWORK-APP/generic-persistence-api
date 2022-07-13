package app.softnetwork.persistence

import akka.actor.typed.ActorRef
import org.json4s.Formats
import app.softnetwork.persistence.model.{Entity, Timestamped}

import scala.language.implicitConversions

/**
  * Created by smanciot on 19/03/2018.
  */
package object message {

  /** Command objects **/
  trait Command

  /**
    * a command which includes a reference to the actor identity to whom a reply has to be sent
    *
    * @tparam R - type of command result
    */
  trait CommandWithReply[R <: CommandResult] extends Command {
    def replyTo: ActorRef[R]
  }

  /**
    * a wrapper arround a command and its reference to the actor identity to whom a reply has to be sent
    *
    * @tparam C - type of command
    * @tparam R - type of command result
    */
  trait CommandWrapper[C <: Command, R <: CommandResult] extends CommandWithReply[R] {
    def command: C
  }

  /**
    * CommandWrapper companion object
    */
  object CommandWrapper {
    def apply[C <: Command, R <: CommandResult](aCommand: C, aReplyTo: ActorRef[R]): C = new CommandWrapper[C, R] {
      override val command: C = aCommand
      override val replyTo: ActorRef[R] = aReplyTo
    }.asInstanceOf[C]
  }

  /** Entity command **/

  /**
    * a command that should be handled by a specific entity
    */
  trait EntityCommand extends Command with Entity {
    def id: String // TODO rename to uuid ?
  }

  /**
    * allow a command to be handled by no specific entity
    */
  trait AllEntities extends EntityCommand {_: Command =>
    override val id: String = ALL_KEY
  }

  /** Event objects **/
  trait Event

  trait BroadcastEvent extends Event {
    def externalUuid: String
  }

  /** Crud events **/
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

  /** Command result **/
  trait CommandResult

  @SerialVersionUID(0L)
  class ErrorMessage(val message: String) extends CommandResult

  case object UnknownCommand extends ErrorMessage("UnknownCommand")

  case object UnknownEvent extends ErrorMessage("UnknownEvent")

  /** Count command result **/
  case class CountResponse(field: String, count: Int, error: Option[String] = None)

  @SerialVersionUID(0L)
  abstract class CountResult(results: Seq[CountResponse]) extends CommandResult

  /** Protobuf events **/
  trait ProtobufEvent extends Event

  /** Cbor events **/
  trait CborEvent extends Event
}
