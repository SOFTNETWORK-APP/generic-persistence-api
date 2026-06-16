package app.softnetwork.persistence.service

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.typed.scaladsl.Patterns
import app.softnetwork.persistence.message.{AuditableCommand, Command, CommandResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Created by smanciot on 15/04/2020.
  */
trait Service[C <: Command, R <: CommandResult] { _: Patterns[C, R] =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContext = system.executionContext

  def run(entityId: String, command: C)(implicit tTag: ClassTag[C]): Future[R] = {
    this ?? (entityId, command)
  }

  /** Story 13.7 — the single shared seam for threading a request/flow `correlationId` onto a
    * command before dispatch. HTTP `serverLogic` (tapir) runs in a `Future` where MDC does not
    * survive (C14), so the id must travel as DATA: the endpoint reads it via
    * `HttpCorrelation.correlationInput` and calls this. No-op for commands that are not
    * [[AuditableCommand]]. The id then rides the command across the cluster-sharding boundary
    * (Kryo) and the handler journals it onto the event's `correlation_id` proto field.
    */
  def runCorrelated(entityId: String, command: C, correlationId: String)(implicit
    tTag: ClassTag[C]
  ): Future[R] = {
    if (correlationId.nonEmpty) command match {
      case a: AuditableCommand => a.withCorrelationId(correlationId)
      case _                   =>
    }
    run(entityId, command)
  }

}
