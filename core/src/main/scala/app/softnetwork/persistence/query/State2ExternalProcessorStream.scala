package app.softnetwork.persistence.query

import _root_.akka.Done
import _root_.akka.persistence.typed.PersistenceId
import akka.actor.typed.eventstream.EventStream.Publish
import com.typesafe.scalalogging.StrictLogging
import app.softnetwork.persistence._
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Timestamped

import scala.concurrent.Future

/**
  * Created by smanciot on 16/05/2020.
  */
trait State2ExternalProcessorStream[T <: Timestamped, E <: CrudEvent] extends EventProcessorStream[E]
  with ManifestWrapper[T] with StrictLogging {_: JournalProvider with ExternalPersistenceProvider[T] =>

  def externalProcessor: String

  /**
    *
    * @return whether or not the events processed by this processor stream would be published to the main bus event
    */
  def forTests: Boolean = false

  lazy val tag: String = s"${getType[T](manifestWrapper.wrapped)}-to-$externalProcessor"

  /**
    *
    * Processing event
    *
    * @param event         - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr    - sequence number
    * @return
    */
  override protected def processEvent(event: E, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    var done = true
    event match {

      case evt: Created[_] =>
        import evt._
        if(!createDocument(document.asInstanceOf[T])){
          logger.error("document {} has not be created by {}", document.uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(evt))
        }

      case evt: Updated[_] =>
        import evt._
        if(!updateDocument(document.asInstanceOf[T], upsert)){
          logger.error("document {} has not be updated by {}", document.uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(evt))
        }

      case evt: Deleted =>
        import evt._
        if(!deleteDocument(uuid)){
          logger.error("document {} has not be deleted by {}", uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(evt))
        }

      case evt: Upserted =>
        if(!upsertDocument(evt.uuid, evt.data)){
          logger.error("document {} has not been upserted by {}", evt.uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(evt))
        }

      case other => logger.warn("{} does not support event [{}]", platformEventProcessorId, other.getClass)
    }

    if(done){
      Future.successful(Done)
    }
    else{
      Future.failed(
        new Exception(
          s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
        )
      )
    }
  }

}
