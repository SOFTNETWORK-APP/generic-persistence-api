package app.softnetwork.scheduler.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.scheduler.message._
import app.softnetwork.scheduler.config.{SchedulerConfig, Settings}
import app.softnetwork.scheduler.model.{CronTab, Schedule}
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Failure

/**
  * Created by smanciot on 04/09/2020.
  */
trait SchedulerTypeKey extends CommandTypeKey[SchedulerCommand]{
  override def TypeKey(implicit tTag: ClassTag[SchedulerCommand]): EntityTypeKey[SchedulerCommand] =
    SchedulerBehavior.TypeKey
}

trait SchedulerHandler extends EntityPattern[SchedulerCommand, SchedulerCommandResult] with SchedulerTypeKey

object SchedulerHandler extends SchedulerHandler

trait SchedulerDao extends Completion { _: SchedulerHandler =>

  lazy val config: SchedulerConfig = Settings.SchedulerConfig

  def start(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    resetScheduler() await {
      case bool if bool =>
        system.scheduler.scheduleWithFixedDelay(
          config.resetCronTabs.initialDelay.seconds,
          config.resetCronTabs.delay.seconds
        )(
          () => !! (ResetCronTabs)
        )
      case _ =>
    } match {
      case Failure(f) =>
        logger.error(f.getMessage, f)
        throw f
      case _ =>
    }
  }

  private[this] def resetScheduler()(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !?(ResetScheduler).map {
      case SchedulerReseted => true
      case _ => false
    }
  }

  private[scheduler] def addSchedule(schedule: Schedule)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (AddSchedule(schedule)).map {
      case ScheduleAdded => true
      case _ => false
    }
  }

  private[scheduler] def addCronTab(cronTab: CronTab)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (AddCronTab(cronTab)).map {
      case CronTabAdded => true
      case _ => false
    }
  }

}

object SchedulerDao extends SchedulerDao with SchedulerHandler
