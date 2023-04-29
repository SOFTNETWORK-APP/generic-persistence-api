package app.softnetwork.persistence.query

import _root_.akka.Done
import _root_.akka.persistence.typed.PersistenceId
import akka.actor.typed.eventstream.EventStream.Publish
import com.typesafe.scalalogging.Logger
import app.softnetwork.persistence._
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Timestamped

import scala.concurrent.Future

/** Created by smanciot on 16/05/2020.
  */
trait State2ExternalProcessorStream[T <: Timestamped, E <: CrudEvent]
    extends EventProcessorStream[E]
    with ManifestWrapper[T] {
  _: JournalProvider with OffsetProvider with ExternalPersistenceProvider[T] =>

  def externalProcessor: String

  /** @return
    *   whether or not the events processed by this processor stream would be published to the main
    *   bus event
    */
  def forTests: Boolean = false

  lazy val tag: String = s"${getType[T](manifestWrapper.wrapped)}-to-$externalProcessor"

  /** Processing event
    *
    * @param event
    *   - event to process
    * @param persistenceId
    *   - persistence id
    * @param sequenceNr
    *   - sequence number
    * @return
    */
  override protected def processEvent(
    event: E,
    persistenceId: PersistenceId,
    sequenceNr: Long
  ): Future[Done] = {
    var done = true
    event match {

      case evt: Created[_] =>
        import evt._
        if (!createDocument(document.asInstanceOf[T])) {
          log.error(s"document ${document.uuid} has not be created by $platformEventProcessorId")
          done = false
        } else if (forTests) {
          system.eventStream.tell(Publish(evt))
        }

      case evt: Updated[_] =>
        import evt._
        if (!updateDocument(document.asInstanceOf[T], upsert)) {
          log.error(s"document ${document.uuid} has not be updated by $platformEventProcessorId")
          done = false
        } else if (forTests) {
          system.eventStream.tell(Publish(evt))
        }

      case evt: Deleted =>
        import evt._
        if (!deleteDocument(uuid)) {
          log.error(s"document $uuid has not be deleted by $platformEventProcessorId")
          done = false
        } else if (forTests) {
          system.eventStream.tell(Publish(evt))
        }

      case evt: Upserted =>
        if (!upsertDocument(evt.uuid, evt.data)) {
          log.error(s"document ${evt.uuid} has not be upserted by $platformEventProcessorId")
          done = false
        } else if (forTests) {
          system.eventStream.tell(Publish(evt))
        }

      case other =>
        log.warn(s"$platformEventProcessorId does not support event [${other.getClass}]")
    }

    if (done) {
      Future.successful(Done)
    } else {
      Future.failed(
        new Exception(
          s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
        )
      )
    }
  }

}
