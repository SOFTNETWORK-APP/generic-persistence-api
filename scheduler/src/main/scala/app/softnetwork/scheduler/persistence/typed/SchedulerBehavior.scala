package app.softnetwork.scheduler.persistence.typed

import java.sql.Timestamp
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder}
import app.softnetwork.persistence._
import app.softnetwork.persistence.typed._
import app.softnetwork.scheduler.config.Settings
import app.softnetwork.scheduler.handlers.SchedulerDao
import app.softnetwork.scheduler.message._
import org.softnetwork.akka.message.SchedulerEvents._
import org.softnetwork.akka.model.Scheduler

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.math._

/**
  * Created by smanciot on 04/09/2020.
  */
private[scheduler] trait SchedulerBehavior extends EntityBehavior[SchedulerCommand, Scheduler, SchedulerEvent, SchedulerCommandResult] {

  lazy val schedulerId: String = Settings.SchedulerConfig.id.getOrElse(ALL_KEY)

  override val emptyState: Option[Scheduler] = None

  override val snapshotInterval: Int = 100

  private def schedulerDao: SchedulerDao = SchedulerDao

  /**
    *
    * @return node role required to start this actor
    */
  override def role: String = Settings.SchedulerConfig.akkaNodeRole

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
      case cmd: ResetCronTabsAndSchedules =>
        def trigerResetCronTabsAndSchedules(scheduler: Scheduler, switch: Boolean)
        : EffectBuilder[SchedulerEvent, Option[Scheduler]] = {
          implicit val system: ActorSystem[_] = context.system
          implicit val ec: ExecutionContextExecutor = system.executionContext
          system.scheduler.scheduleOnce(
            Settings.SchedulerConfig.resetCronTabs.delay.seconds,
            () => schedulerDao.resetCronTabsAndSchedules(resetScheduler = false)
          )
          Effect.persist(
            ResetCronTabsAndSchedulesUpdatedEvent(switch, now())
          ).thenRun(_ => CronTabsAndSchedulesReseted ~> replyTo)
        }
        state match {
          case Some(scheduler) if !scheduler.getTriggerResetCronTabsAndSchedules || !cmd.resetScheduler =>
            if(scheduler.lastCronTabsAndSchedulesReseted.isEmpty ||
              ((now().getTime - scheduler.getLastCronTabsAndSchedulesReseted.getTime) >
                Settings.SchedulerConfig.resetCronTabs.delay * 1000)){
              scheduler.cronTabs.foreach{cronTab =>
                context.self ! AddCronTab(cronTab)
              }
              scheduler.schedules.filter(_.scheduledDate.isDefined).foreach{schedule =>
                context.self ! AddSchedule(schedule)
              }
              context.log.info(s"${scheduler.cronTabs.size} cron tabs and ${scheduler.schedules.size} schedules reseted")
              trigerResetCronTabsAndSchedules(scheduler, switch = !scheduler.getTriggerResetCronTabsAndSchedules)
            }
            else{
              Effect.none.thenRun(_ => CronTabsAndSchedulesNotReseted ~> replyTo)
            }
          case Some(scheduler) if scheduler.lastCronTabsAndSchedulesReseted.isEmpty ||
            ((now().getTime - scheduler.getLastCronTabsAndSchedulesReseted.getTime) >
              Settings.SchedulerConfig.resetCronTabs.delay * 1000) =>
            trigerResetCronTabsAndSchedules(scheduler, switch = false)
          case _ => Effect.none.thenRun(_ => CronTabsAndSchedulesNotReseted ~> replyTo)
        }
      case ResetScheduler => // add all schedules
        state match {
          case Some(scheduler) =>
            val temp = scheduler.cronTabs.groupBy(_.persistenceId)
              .map(kv => (kv._1, kv._2.groupBy(_.entityId).map(cts => (cts._1, cts._2.map(_.key).toSet))))
            val events: List[SchedulerEvent] = (for(
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
          case _ => Effect.persist(SchedulerInitializedEvent(now())).thenRun(_ => {
            context.log.info("Scheduler not reseted")
            SchedulerNotFound ~> replyTo
          })
        }
      // add a new schedule which will be triggered either after the delay specified in seconds or when the specified scheduled date has been reached
      case cmd: AddSchedule =>
        import cmd._
        val updatedSchedule =
          state match {
            case Some(scheduler) =>
              scheduler.schedules.find(_.uuid == schedule.uuid) match {
                case Some(s) => schedule.copy(lastTriggered = s.lastTriggered)
                case _ => schedule.copy(lastTriggered = None)
              }
            case _ => schedule
          }
        Effect.persist(
            ScheduleAddedEvent(updatedSchedule)
        ).thenRun(
          _ => {
            if(updatedSchedule.triggerable){
              context.log.info(s"Triggering schedule $updatedSchedule")
              timers.startSingleTimer(
                updatedSchedule.uuid,
                TriggerSchedule(updatedSchedule.persistenceId, updatedSchedule.entityId, updatedSchedule.key),
                updatedSchedule.delay.seconds
              )
            }
            else{
              context.log.warn(s"Schedule $updatedSchedule has not been triggered")
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
                Effect.persist(
                  if(updatedSchedule.triggerable ||
                    (updatedSchedule.scheduledDate.isDefined && now().before(updatedSchedule.getScheduledDate))
                  ){
                    List(
                      ScheduleAddedEvent(updatedSchedule) // update schedule
                    )
                  }
                  else{
                    List(
                      ScheduleRemovedEvent(updatedSchedule.persistenceId, updatedSchedule.entityId, updatedSchedule.key)
                    )
                  } :+ ScheduleTriggeredEvent(updatedSchedule)
                ).thenRun(_ => {
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
                val events: List[SchedulerEvent] =
                  if(schedule.entityId == ALL_KEY){
                    scheduler.schedules.filter(
                      s => s.entityId != ALL_KEY && s.persistenceId == schedule.persistenceId && s.key == schedule.key
                    ).map(ct => ScheduleRemovedEvent(ct.persistenceId, ct.entityId, ct.key)).toList
                  }
                  else{
                    List.empty
                  }
                Effect.persist(
                  events :+ ScheduleRemovedEvent(cmd.persistenceId, cmd.entityId, cmd.key)
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
                // next trigger has to be (re)calculated
                if(cronTab.nextTriggered.isEmpty ||
                  cronTab.cron != cmd.cronTab.cron ||
                  (cronTab.lastTriggered.isDefined &&
                    cronTab.getNextTriggered.getTime == cronTab.getLastTriggered.getTime)
                ){
                  cmd.cronTab.nextLocalDateTime() match {
                    case Some(ldt) =>
                      val updatedCronTab =
                        cronTab
                          .withCron(cmd.cronTab.cron)
                          .withNextTriggered(Timestamp.valueOf(ldt))
                      context.log.info(s"$updatedCronTab updated")
                      Some(updatedCronTab)
                    case _ => None
                  }
                }
                // next trigger already defined
                else {
                  Some(cronTab)
                }
              case _ => // new cron tab
                // next trigger undefined
                if(cmd.cronTab.nextTriggered.isEmpty){
                  cmd.cronTab.nextLocalDateTime() match {
                    case Some(ldt) =>
                      val updatedCronTab = cmd.cronTab.withNextTriggered(Timestamp.valueOf(ldt)).copy(lastTriggered = None)
                      context.log.info(s"$updatedCronTab added")
                      Some(updatedCronTab)
                    case _ => None
                  }
                }
                else{
                  context.log.info(s"${cmd.cronTab} added")
                  Some(cmd.cronTab.copy(lastTriggered = None))
                }
            }
          case _ => None
        }) match {
          case Some(cronTab) =>
            def runner: Option[Scheduler] => Unit = _ => {
              if(!now().before(cronTab.getNextTriggered)){
                if(!timers.isTimerActive(cronTab.uuid)) {
                  val ignored = cronTab.lastTriggered.isDefined &&
                    (abs(now().getTime - cronTab.getLastTriggered.getTime) * 1000 < 120 * 1000 ||
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
            Effect.persist(
              CronTabAddedEvent(cronTab)
            ).thenRun(state => runner(state))
          case _ => Effect.none.thenRun(_ => CronTabNotAdded ~> replyTo)
        }
      case cmd: TriggerCronTab => // trigger the cron tab
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.uuid
            ) match {
              case Some(cronTab) =>
                val updatedCronTab =
                  cronTab
                    .withLastTriggered(cronTab.getNextTriggered)
                    .copy(
                      nextTriggered =
                        cronTab.nextLocalDateTime() match {
                          case Some(ldt) => Some(Timestamp.valueOf(ldt))
                          case _ => None
                        }
                    )
                Effect.persist(
                  List(
                    CronTabAddedEvent(updatedCronTab), // update cron tab
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
                val events: List[SchedulerEvent] =
                  if(cronTab.entityId == ALL_KEY){
                    scheduler.cronTabs.filter(
                      ct => ct.entityId != ALL_KEY && ct.persistenceId == cronTab.persistenceId && ct.key == cronTab.key
                    ).map(ct => CronTabRemovedEvent(ct.persistenceId, ct.entityId, ct.key)).toList
                  }
                  else{
                    List.empty
                  }
                Effect.persist(
                  events :+ CronTabRemovedEvent(cmd.persistenceId, cmd.entityId, cmd.key)
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
    implicit context: ActorContext[_]): Option[Scheduler] =
    event match {
      case evt: ScheduleAddedEvent =>
        Option(state.map(s => s.copy(
          schedules =
            s.schedules.filterNot(schedule =>
              schedule.uuid == evt.schedule.uuid
            ) :+ evt.schedule
        )).getOrElse(Scheduler(schedulerId, schedules = Seq(evt.schedule))))
      case evt: ScheduleRemovedEvent =>
        Option(state.map(s => s.copy(
          schedules = s.schedules.filterNot(schedule =>
            schedule.uuid == evt.uuid
          )
        )).getOrElse(Scheduler(schedulerId)))
      case evt: CronTabAddedEvent =>
        Option(state.map(s => s.copy(
          cronTabs =
            s.cronTabs.filterNot(cronTab =>
              cronTab.uuid == evt.cronTab.uuid
            ) :+ evt.cronTab
        )).getOrElse(Scheduler(schedulerId, cronTabs = Seq(evt.cronTab))))
      case evt: CronTabRemovedEvent =>
        Option(state.map(s => s.copy(
          cronTabs = s.cronTabs.filterNot(cronTab =>
            cronTab.uuid == evt.uuid
          )
        )).getOrElse(Scheduler(schedulerId)))
      case evt: ResetCronTabsAndSchedulesUpdatedEvent =>
        Option(state.map(s => s
          .withTriggerResetCronTabsAndSchedules(evt.triggerResetCronTabsAndSchedules)
          .withLastCronTabsAndSchedulesReseted(evt.lastTriggered)
        ).getOrElse(Scheduler(schedulerId)))
      case _: SchedulerInitializedEvent =>
        Some(Scheduler(schedulerId, Seq.empty, Seq.empty))
      case _ => super.handleEvent(state, event)
    }
}

object SchedulerBehavior extends SchedulerBehavior {
  override val persistenceId: String = "Scheduler"
}
