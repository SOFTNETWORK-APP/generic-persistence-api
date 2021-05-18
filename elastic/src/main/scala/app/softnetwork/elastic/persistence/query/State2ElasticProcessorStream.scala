package app.softnetwork.elastic.persistence.query

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish

import akka.persistence.typed.PersistenceId

import app.softnetwork.persistence.ManifestWrapper

import app.softnetwork.persistence.query.{State2ExternalProcessorStream, JournalProvider}

import app.softnetwork.elastic.message._

import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.persistence.message._

import scala.concurrent.Future

/**
  * Created by smanciot on 16/05/2020.
  */
trait State2ElasticProcessorStream[T <: Timestamped, E <: CrudEvent] extends State2ExternalProcessorStream[T, E]
  with ManifestWrapper[T] {_: JournalProvider with ElasticProvider[T] =>

  override val externalProcessor = "elastic"

  def forTests: Boolean = false

  override protected def init(): Unit = {
    initIndex()
  }

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
          system.eventStream.tell(Publish(DocumentCreated(document.uuid)))
        }

      case evt: Updated[_] =>
        import evt._
        if(!updateDocument(document.asInstanceOf[T])){
          logger.error("document {} has not be updated by {}", document.uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(DocumentUpdated(document.uuid)))
        }

      case evt: Deleted =>
        import evt._
        if(!deleteDocument(uuid)){
          logger.error("document {} has not be deleted by {}", uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(DocumentDeleted(uuid)))
        }

      case evt: Upserted =>
        if(!upsertDocument(evt.uuid, evt.data)){
          logger.error("document {} has not been upserted by {}", evt.uuid, platformEventProcessorId)
          done = false
        }
        else if(forTests){
          system.eventStream.tell(Publish(DocumentUpserted(evt.uuid)))
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

