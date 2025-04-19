package app.softnetwork.persistence.jdbc.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.persistence.query.{
  JournalProvider,
  OffsetProvider,
  State2ExternalProcessorStream
}

import scala.concurrent.ExecutionContext

/** Created by smanciot on 16/05/2020.
  */
trait State2JdbcProcessorStream[T <: Timestamped, E <: CrudEvent]
    extends State2ExternalProcessorStream[T, E]
    with ManifestWrapper[T] { _: JournalProvider with OffsetProvider with JdbcStateProvider[T] =>

  override val externalProcessor = "jdbc"

  override implicit lazy val executionContext: ExecutionContext = system.executionContext

  override protected def init(): Unit = {
    initTable()
  }

}
