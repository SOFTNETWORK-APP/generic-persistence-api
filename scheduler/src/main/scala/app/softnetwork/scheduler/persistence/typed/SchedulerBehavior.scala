package app.softnetwork.scheduler.persistence.typed

import java.sql.Timestamp

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{TimerScheduler, ActorContext}
import akka.persistence.typed.scaladsl.Effect

import app.softnetwork.persistence._

import app.softnetwork.persistence.typed._
import app.softnetwork.scheduler.message._

import org.softnetwork.akka.message.SchedulerEvents._
import org.softnetwork.akka.message.scheduler._
import org.softnetwork.akka.model.Scheduler

import scala.concurrent.duration._
import scala.math._

/**
  * Created by smanciot on 04/09/2020.
  */
trait SchedulerBehavior extends EntityBehavior[SchedulerCommand, Scheduler, SchedulerEvent, SchedulerCommandResult] {

  override val emptyState: Option[Scheduler] = Some(Scheduler(ALL_KEY, Seq.empty, Seq.empty))

  override val snapshotInterval: Int = 100

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event    - the event to tag
    * @return event tags
    */
  override protected def tagEvent(entityId: String, event: SchedulerEvent): Set[String] =
    event match {
      case e: ScheduleTriggeredEvent => Set(s"${e.schedule.persistenceId}-scheduler")
      case e: CronTabTriggeredEvent => Set(s"${e.cronTab.persistenceId}-scheduler")
      case e: CronTabsResetedEvent => Set(s"${e.persistenceId}-scheduler")
      case _ => super.tagEvent(entityId, event)
    }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @param timers   - scheduled messages associated with this entity behavior
    * @return effect
    */
  override def handleCommand(entityId: String, state: Option[Scheduler], command: SchedulerCommand,
                             replyTo: Option[ActorRef[SchedulerCommandResult]], timers: TimerScheduler[SchedulerCommand]
                            )(implicit context: ActorContext[SchedulerCommand]
  ): Effect[SchedulerEvent, Option[Scheduler]] =
    command match {
      case ResetCronTabs =>
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.foreach{cronTab =>
              context.self ! AddCronTab(cronTab)
            }
            scheduler.schedules.filter(_.scheduledDate.isDefined).foreach{schedule =>
              context.self ! AddSchedule(schedule)
            }
            context.log.info(s"${scheduler.cronTabs.size} cron tabs and ${scheduler.schedules.size} schedules reseted")
          case _ =>
        }
        Effect.none
      case ResetScheduler => // add all schedules
        state match {
          case Some(scheduler) =>
            val temp = scheduler.cronTabs.groupBy(_.persistenceId).map(kv => (kv._1, kv._2.groupBy(_.entityId).map(cts => (cts._1, cts._2.map(_.key).toSet))))
            val events: List[CronTabsResetedEvent] = (for(
              persistenceId <- temp.keys;
              (entityId, keys) <- temp(persistenceId)
            ) yield CronTabsResetedEvent(persistenceId, entityId, keys.toSeq)).toList
            Effect.persist(events).thenRun(_ => {
              scheduler.schedules.foreach{schedule =>
                context.self ! AddSchedule(schedule)
              }
              context.log.info("Scheduler reseted")
              SchedulerReseted ~> replyTo
            })
          case _ => Effect.none.thenRun(_ => {
            context.log.info("Scheduler not reseted")
            SchedulerNotFound ~> replyTo
          })
        }
      // add a new schedule which will be triggered either repeatedly or once
      // after the delay specified in seconds or at the specified scheduled date
      case cmd: AddSchedule =>
        import cmd._
        val updatedSchedule =
          state match {
            case Some(scheduler) =>
              scheduler.schedules.find(_.uuid == schedule.uuid) match {
                case Some(s) => schedule.copy(lastTriggered = s.lastTriggered)
                case _ => schedule
              }
            case _ => schedule
          }
        val events: List[SchedulerEvent] =
          if(updatedSchedule.entityId == ALL_KEY){
            state.get.schedules.filter(
              s => s.entityId != ALL_KEY && s.persistenceId == updatedSchedule.persistenceId && s.key == updatedSchedule.key
            ).map(ct => ScheduleRemovedEvent(ct.persistenceId, ct.entityId, ct.key)).toList
          }
          else{
            List.empty
          }
        Effect.persist(
          List(
            ScheduleAddedEvent(updatedSchedule)
          ) ++ events
        ).thenRun(
          state => {
            import updatedSchedule._
            val ignored = lastTriggered.isDefined && (now().getTime - getLastTriggered.getTime) * 1000 < 120
            if(!ignored){
              if(scheduledDate.isDefined){
                // trigger schedule only if the scheduled date has been reached and the schedule has never been triggered
                if(now().after(scheduledDate.get)){
                  if(lastTriggered.isEmpty || lastTriggered.get.before(scheduledDate.get)){
                    context.log.info(s"Triggering schedule $updatedSchedule")
                    timers.startSingleTimer(
                      uuid,
                      TriggerSchedule(schedule.persistenceId, schedule.entityId, key),
                      delay.seconds
                    )
                  }
                }
              }
              else if(repeatedly.getOrElse(false)){
                context.log.debug(s"Triggering schedule $updatedSchedule")
                timers.startTimerWithFixedDelay(
                  uuid,
                  TriggerSchedule(schedule.persistenceId, schedule.entityId, key),
                  delay.seconds
                )
              }
              else{
                context.log.debug(s"Triggering schedule $updatedSchedule")
                timers.startSingleTimer(
                  uuid,
                  TriggerSchedule(schedule.persistenceId, schedule.entityId, key),
                  delay.seconds
                )
              }
            }
            context.log.debug(s"$schedule added")
            ScheduleAdded ~> replyTo
          }
        )
      case cmd: TriggerSchedule => // effectively trigger the schedule
        state match {
          case Some(scheduler) =>
            scheduler.schedules.find(schedule =>
              schedule.uuid == cmd.uuid
            ) match {
              case Some(schedule) =>
                val updatedSchedule =
                  if(schedule.scheduledDate.isDefined && schedule.getScheduledDate.before(now())){
                    schedule.withLastTriggered(schedule.getScheduledDate)
                  }
                  else{
                    schedule.withLastTriggered(now())
                  }
                Effect.persist(ScheduleTriggeredEvent(updatedSchedule)).thenRun(_ => {
                  context.log.info(s"$schedule triggered at ${updatedSchedule.getLastTriggered}")
                  ScheduleTriggered ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => ScheduleNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case cmd: RemoveSchedule =>
        state match {
          case Some(scheduler) =>
            scheduler.schedules.find(schedule =>
              schedule.uuid == cmd.uuid
            ) match {
              case Some(schedule) =>
                Effect.persist(
                  List(
                    ScheduleRemovedEvent(cmd.persistenceId, cmd.entityId, cmd.key)
                  )
                ).thenRun(_ => {
                  timers.cancel(schedule.uuid)
                  context.log.debug(s"$schedule removed")
                  ScheduleRemoved ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => ScheduleNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case cmd: AddCronTab => // add a new cron tab which will be triggered at the calculated date
        (state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.cronTab.uuid
            ) match {
              case Some(cronTab) =>
                // next trigger undefined
                if(cronTab.nextTriggered.isEmpty ||
                  cronTab.cron != cmd.cronTab.cron ||
                  cronTab.getNextTriggered.before(now())){
                  cmd.cronTab.nextLocalDateTime() match {
                    case Some(ldt) =>
                      Some(
                        (cronTab
                          .withCron(cmd.cronTab.cron)
                          .withNextTriggered(Timestamp.valueOf(ldt)),
                          true)
                      )
                    case _ => None
                  }
                }
                // next trigger already defined
                else {
                  Some((cronTab, false))
                }
              case _ => // new cron tab
                cmd.cronTab.nextLocalDateTime() match {
                  case Some(ldt) =>
                    val updatedCronTab = cmd.cronTab.withNextTriggered(Timestamp.valueOf(ldt))
                    context.log.info(s"$updatedCronTab added")
                    Some((updatedCronTab, true))
                  case _ => None
                }
            }
          case _ => None
        }) match {
          case Some((cronTab, persist)) =>
            def runner(state: Option[Scheduler]) = {
              if(!now().before(cronTab.getNextTriggered)){
                if(!timers.isTimerActive(cronTab.uuid)) {
                  val ignored = cronTab.lastTriggered.isDefined &&
                    (abs(now().getTime - cronTab.getLastTriggered.getTime) * 1000 < 120 ||
                      cronTab.getNextTriggered.getTime == cronTab.getLastTriggered.getTime)
                  if(!ignored){
                    context.log.info(s"Triggering cron tab $cronTab")
                    timers.startSingleTimer(
                      cronTab.uuid,
                      TriggerCronTab(cronTab.persistenceId, cronTab.entityId, cronTab.key),
                      1.second
                    )
                    context.log.info(s"CronTab $cronTab started at ${now()}")
                  }
                  else{
                    context.log.debug(s"CronTab $cronTab has been ignored")
                  }
                }
              }
              else{
                context.log.debug(s"CronTab $cronTab will not be triggered")
              }
              CronTabAdded ~> replyTo
            }
            val events: List[SchedulerEvent] = 
              if(cronTab.entityId == ALL_KEY){
                state.get.cronTabs.filter(
                  ct => ct.entityId != ALL_KEY && ct.persistenceId == cronTab.persistenceId && ct.key == cronTab.key
                ).map(ct => CronTabRemovedEvent(ct.persistenceId, ct.entityId, ct.key)).toList
              }
              else{
                List.empty
              }
            if(persist){
              Effect.persist(
                List(
                  CronTabAddedEvent(cronTab)
                ) ++ events
              ).thenRun(state => runner(state))
            }
            else if(events.nonEmpty){
              Effect.persist(events).thenRun(state => runner(state))
            }
            else{
              Effect.none.thenRun(state => runner(state))
            }
          case _ => Effect.none.thenRun(_ => CronTabNotAdded ~> replyTo)
        }
      case cmd: TriggerCronTab => // trigger the cron tab
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.uuid
            ) match {
              case Some(cronTab) =>
                val updatedCronTab = cronTab
                  .withLastTriggered(
                    if(now().after(cronTab.getNextTriggered))
                      cronTab.getNextTriggered
                    else
                      now()
                  )
                  .withNextTriggered(
                    cronTab.nextLocalDateTime() match {
                      case Some(ldt) => Timestamp.valueOf(ldt)
                      case _ => cronTab.getNextTriggered
                    }
                  )
                Effect.persist(
                  List(
                    CronTabAddedEvent(updatedCronTab),
                    CronTabTriggeredEvent(updatedCronTab)
                  )
                ).thenRun(_ => {
                  context.log.info(s"$cronTab triggered at ${updatedCronTab.getLastTriggered}")
                  CronTabTriggered ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => CronTabNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case cmd: RemoveCronTab =>
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.uuid
            ) match {
              case Some(cronTab) =>
                Effect.persist(
                  List(
                    CronTabRemovedEvent(cmd.persistenceId, cmd.entityId, cmd.key)
                  )
                ).thenRun(_ => {
                  timers.cancel(cronTab.uuid)
                  context.log.info(s"$cronTab removed")
                  CronTabRemoved ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => CronTabNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case LoadScheduler =>
        state match {
          case Some(scheduler) =>  Effect.none.thenRun(_ => SchedulerLoaded(scheduler) ~> replyTo)
          case _ =>  Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[Scheduler], event: SchedulerEvent)(
    implicit context: ActorContext[SchedulerCommand]): Option[Scheduler] =
    event match {
      case evt: ScheduleAddedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                schedules =
                  s.schedules.filterNot(schedule =>
                    schedule.uuid == evt.schedule.uuid
                  ) :+ evt.schedule
              )
            case _ => Scheduler(ALL_KEY, schedules = Seq(evt.schedule))
          }
        )
      case evt: ScheduleRemovedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                schedules = s.schedules.filterNot(schedule =>
                  schedule.uuid == evt.uuid
                )
              )
            case _ => Scheduler(ALL_KEY)
          }
        )
      case evt: CronTabAddedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                cronTabs =
                  s.cronTabs.filterNot(cronTab =>
                    cronTab.uuid == evt.cronTab.uuid
                  ) :+ evt.cronTab
              )
            case _ => Scheduler(ALL_KEY, cronTabs = Seq(evt.cronTab))
          }
        )
      case evt: CronTabRemovedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                cronTabs = s.cronTabs.filterNot(cronTab =>
                  cronTab.uuid == evt.uuid
                )
              )
            case _ => Scheduler(ALL_KEY)
          }
        )
      case _ => super.handleEvent(state, event)
    }
}

object SchedulerBehavior extends SchedulerBehavior {
  override val persistenceId: String = "Scheduler"
}
