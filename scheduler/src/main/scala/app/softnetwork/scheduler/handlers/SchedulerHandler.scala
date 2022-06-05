package app.softnetwork.scheduler.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.scheduler.message._
import org.softnetwork.akka.message.scheduler._
import app.softnetwork.scheduler.config.{SchedulerConfig, Settings}
import org.softnetwork.akka.model.{CronTab, Scheduler}
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * Created by smanciot on 04/09/2020.
  */
trait SchedulerTypeKey extends CommandTypeKey[SchedulerCommand]{
  override def TypeKey(implicit tTag: ClassTag[SchedulerCommand]): EntityTypeKey[SchedulerCommand] =
    SchedulerBehavior.TypeKey
}

trait SchedulerHandler extends EntityPattern[SchedulerCommand, SchedulerCommandResult] with SchedulerTypeKey

object SchedulerHandler extends SchedulerHandler

trait SchedulerDao { _: SchedulerHandler =>

  lazy val config: SchedulerConfig = Settings.SchedulerConfig

  def start(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    resetScheduler().onComplete {
      case Success(bool) if bool =>
        system.scheduler.scheduleWithFixedDelay(
          config.resetCronTabs.initialDelay.seconds,
          config.resetCronTabs.delay.seconds
        )(
          () => !! (ResetCronTabs)
        )
      case Failure(f) =>
        logger.error(f.getMessage, f)
        throw f
    }
  }

  private[this] def resetScheduler()(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !?(ResetScheduler).map {
      case SchedulerReseted => true
      case _ => false
    }
  }

  def addCronTab(cronTab: CronTab)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (AddCronTab(cronTab)).map {
      case CronTabAdded => true
      case _ => false
    }
  }

  def removeCronTab(persistenceId: String, entityId: String, key: String)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (RemoveCronTab(persistenceId, entityId, key)).map {
      case CronTabRemoved => true
      case _ => false
    }
  }

  def loadScheduler()(implicit system: ActorSystem[_]): Future[Option[Scheduler]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (LoadScheduler).map {
      case r: SchedulerLoaded => Some(r.schdeduler)
      case _ => None
    }
  }
}

object SchedulerDao extends SchedulerDao with SchedulerHandler
