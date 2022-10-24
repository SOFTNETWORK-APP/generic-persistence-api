package app.softnetwork.scheduler

import akka.actor.typed.scaladsl.TimerScheduler
import app.softnetwork.persistence.message.{Command, CommandResult, EntityCommand, ErrorMessage}
import app.softnetwork.scheduler.config.Settings
import app.softnetwork.scheduler.model.{CronTabItem, SchedulerItem}
import org.softnetwork.akka.model.{CronTab, Schedule, Scheduler}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

/**
  * Created by smanciot on 10/05/2021.
  */
package object message {

  /**
    * To schedule a message to be sent repeatedly to the self actor as long as the cron expression is satisfied (
    * as long as next() will return Some)
    * key - a unique identifier for the underlying schedule
    * cronExpression - a simplified subset of the time expression part of the V7 cron expression standard
    */
  abstract class CronTabCommand(val key: Any, val cron: String) extends Command with CronTabItem

  /**
    * CronTabCommand companion object
    */
  object CronTabCommand {
    def apply(key: Any, cron: String): CronTabCommand = {
      new CronTabCommand(key, cron){}
    }
  }

  /**
    * To schedule a message to be sent either once after the given `delay` or repeatedly with a
    * fixed delay between messages to the self actor
    *
    * @tparam C - the type of Command to schedule
    */
  case class ScheduleCommand[C <: Command](key: Any, command: C, maybeDelay: Option[FiniteDuration] = None, once: Boolean = false) {
    def timer : TimerScheduler[C] => Unit = timers => {
      maybeDelay match {
        case Some(delay) =>
          if(once){
            timers.startSingleTimer(key, command, delay)
          }
          else{
            timers.startTimerWithFixedDelay(key, command, delay)
          }
        case _ =>
      }
    }
  }

  /**
    * Schedule companion object
    */
  object ScheduleCommand {

    implicit def cronTabCommandToSchedule[C <: Command](cronTabCommand: CronTabCommand): ScheduleCommand[C] = {
      ScheduleCommand(
        cronTabCommand.key,
        cronTabCommand.asInstanceOf[C],
        cronTabCommand.next(),
        once = true
      )
    }
  }

  trait SchedulerCommand extends EntityCommand {
    override lazy val id: String = Settings.SchedulerConfig.id.getOrElse(ALL_KEY)
  }

  private[scheduler] case class ResetCronTabsAndSchedules(resetScheduler: Boolean = false) extends SchedulerCommand

  private[scheduler] case object ResetScheduler extends SchedulerCommand

  case class TriggerSchedule(persistenceId: String, entityId: String, key: String) extends SchedulerCommand
    with SchedulerItem

  case class TriggerCronTab(persistenceId: String, entityId: String, key: String) extends SchedulerCommand
    with SchedulerItem

  case object LoadScheduler extends SchedulerCommand

  sealed trait SchedulerCommandResult extends CommandResult

  case object SchedulerReseted extends SchedulerCommandResult

  case object CronTabsAndSchedulesReseted extends SchedulerCommandResult

  case class ScheduleAdded(schedule: Schedule) extends SchedulerCommandResult

  case class ScheduleTriggered(schedule: Schedule) extends SchedulerCommandResult

  case class ScheduleRemoved(schedule: Schedule) extends SchedulerCommandResult

  case class CronTabAdded(cronTab: CronTab) extends SchedulerCommandResult

  case class CronTabTriggered(cronTab: CronTab) extends SchedulerCommandResult

  case class CronTabRemoved(cronTab: CronTab) extends SchedulerCommandResult

  case class SchedulerLoaded(scheduler: Scheduler) extends SchedulerCommandResult

  class SchedulerErrorMessage (override val message: String) extends ErrorMessage(message) with SchedulerCommandResult

  case object SchedulerNotFound extends SchedulerErrorMessage("SchedulerNotFound")

  case object ScheduleNotFound extends SchedulerErrorMessage("ScheduleNotFound")

  case object ScheduleNotAdded extends SchedulerErrorMessage("ScheduleNotAdded")

  case object CronTabNotFound extends SchedulerErrorMessage("CronTabNotFound")

  case object CronTabNotAdded extends SchedulerErrorMessage("CronTabNotAdded")

  case object CronTabsAndSchedulesNotReseted extends SchedulerErrorMessage("CronTabsAndSchedulesNotReseted")
}
