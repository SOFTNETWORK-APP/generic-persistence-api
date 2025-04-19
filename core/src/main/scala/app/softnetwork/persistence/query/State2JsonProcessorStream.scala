package app.softnetwork.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Timestamped

/** Created by smanciot on 16/05/2020.
  */
trait State2JsonProcessorStream[T <: Timestamped, E <: CrudEvent]
    extends State2ExternalProcessorStream[T, E]
    with ManifestWrapper[T] { _: JournalProvider with OffsetProvider with JsonProvider[T] =>

  override val externalProcessor = "json"

}
