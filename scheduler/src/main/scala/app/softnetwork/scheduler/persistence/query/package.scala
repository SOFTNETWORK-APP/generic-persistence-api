package app.softnetwork.scheduler.persistence

import akka.Done
import akka.actor.typed.eventstream.EventStream.Publish
import akka.persistence.typed.PersistenceId
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.query.{JournalProvider, EventProcessorStream}
import app.softnetwork.persistence.message.{CommandResult, Command}
import app.softnetwork.scheduler.message._

import org.softnetwork.akka.message.SchedulerEvents._
import org.softnetwork.akka.model.{CronTab, Schedule}

import scala.concurrent.Future

package query {

  /**
   * Created by smanciot on 04/09/2020.
   */
  trait Scheduler2EntityProcessorStream[C <: Command, R <: CommandResult] extends EventProcessorStream[SchedulerEvent] {
    _: JournalProvider with EntityPattern[C, R] =>
    /**
      *
      * Processing event
      *
      * @param event         - event to process
      * @param persistenceId - persistence id
      * @param sequenceNr    - sequence number
      * @return
      */
    override protected final def processEvent(
                                               event: SchedulerEvent,
                                               persistenceId: PersistenceId,
                                               sequenceNr: Long): Future[Done] = {
      event match {
        case evt: ScheduleTriggeredEvent =>
          import evt._
          if(schedule.entityId == ALL_KEY){
            currentPersistenceIds().runForeach(persistenceId => {
              if(persistenceId.startsWith(schedule.persistenceId)){
                val entityId = persistenceId.split("\\|").last
                if(entityId != ALL_KEY){
                  val entitySchedule = schedule.withEntityId(entityId)
                  logger.info(s"$entitySchedule started at ${now()}")
                  triggerSchedule(entitySchedule)
                }
                else{
                  Future.successful(true)
                }
              }
              else{
                Future.successful(true)
              }
            })
          }
          else {
            triggerSchedule(schedule).map{
              case true => Done
              case _ =>
                throw new Exception(
                  s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
                )
            }
          }
        case evt: CronTabTriggeredEvent =>
          import evt._
          if(cronTab.entityId == ALL_KEY){
            currentPersistenceIds().runForeach(persistenceId => {
              if(persistenceId.startsWith(cronTab.persistenceId)){
                val entityId = persistenceId.split("\\|").last
                if(entityId != ALL_KEY){
                  val entityCronTab = cronTab.withEntityId(entityId)
                  logger.info(s"$entityCronTab started at ${now()}")
                  triggerCronTab(entityCronTab)
                }
                else{
                  Future.successful(true)
                }
              }
              else{
                Future.successful(true)
              }
            })
          }
          else {
            triggerCronTab(cronTab).map{
              case true => Done
              case _ =>
                throw new Exception(
                  s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
                )
            }
          }
        case evt: CronTabsResetedEvent =>
          resetCronTabs(evt.entityId, evt.keys).map{
            case true => Done
            case _ =>
              throw new Exception(
                s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
              )
          }
        case _ => Future.successful(Done)
      }
    }

    /**
      *
      * @param schedule - the schedule to trigger
      * @return true if the schedule has been successfully triggered, false otherwise
      */
    protected def triggerSchedule(schedule: Schedule): Future[Boolean] = Future.successful(false)

    /**
      *
      * @param cronTab - the cron tab to trigger
      * @return true if the cron tab has been successfully triggered, false otherwise
      */
    protected def triggerCronTab(cronTab: CronTab): Future[Boolean] = Future.successful(false)

    /**
      *
      * @param entityId - the persistence entity id
      * @return true if the cron tabs have been successfully reseted for this entity, false otherwise
      */
    protected def resetCronTabs(entityId: String, keys: Seq[String] = Seq.empty): Future[Boolean] = Future.successful(false)
  }

  trait Entity2SchedulerProcessorStream extends EventProcessorStream[SchedulerEventWithCommand] {
    _: JournalProvider with EntityPattern[SchedulerCommand, SchedulerCommandResult] =>

    protected val forTests = false

    /**
      *
      * Processing event
      *
      * @param event         - event to process
      * @param persistenceId - persistence id
      * @param sequenceNr    - sequence number
      * @return
      */
    override protected def processEvent(event: SchedulerEventWithCommand, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
      (this !? event.command).map {
        case r: ScheduleNotFound.type =>
          logger.warn(s"${event.command} -> ${r.message}")
          Done
        case r: CronTabNotFound.type =>
          logger.warn(s"${event.command} -> ${r.message}")
          Done
        case r: SchedulerErrorMessage =>
          logger.error(s"${event.command} -> ${r.message}")
          throw new Throwable(s"${event.command} -> ${r.message}")
        case result =>
          if(forTests){
            system.eventStream.tell(Publish(result))
          }
          Done
      }
    }
  }
}