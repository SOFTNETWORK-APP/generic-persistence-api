package app.softnetwork.scheduler.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence._
import app.softnetwork.scheduler.scalatest.SchedulerTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.scheduler.message._
import org.softnetwork.akka.model.{CronTab, Schedule}

import scala.concurrent.ExecutionContextExecutor

/**
  * Created by smanciot on 19/03/2020.
  */
class SchedulerHandlerSpec extends SchedulerHandler with AnyWordSpecLike with SchedulerTestKit {

  implicit lazy val system: ActorSystem[Nothing] = typedSystem()

  implicit lazy val ec: ExecutionContextExecutor = system.executionContext

  "Scheduler" must {
    "add Cron Tab" in {
      val cronTab = CronTab("p", ALL_KEY, "add", "* * * * *")
      this !? AddCronTab(cronTab) assert {
        case CronTabAdded => succeed
        case other => fail(other.getClass)
      }
      this !? AddCronTab(cronTab) assert {
        case CronTabAdded => succeed
        case other => fail(other.getClass)
      }
      this !? LoadScheduler assert {
        case r: SchedulerLoaded => assert(r.scheduler.cronTabs.exists(ct => ct.uuid == cronTab.uuid))
        case _ => fail()
      }
    }
    "not add schedule that is not repeatable and has already been triggered" in {
      val schedule = Schedule("p", "0", "add", 1, None, None, Some(now()))
      this !? AddSchedule(schedule) assert {
        case ScheduleNotAdded => succeed
        case other => fail(other.getClass)
      }
    }
    "add schedule that is repeatable" in {
      val schedule = Schedule("p", "1", "add", 1, Some(true), None, Some(now()))
      this !? AddSchedule(schedule) assert {
        case ScheduleAdded => succeed
        case other => fail(other.getClass)
      }
    }
    "add schedule that is not repeatable and has never been triggered" in {
      val schedule = Schedule("p", "2", "add", 1)
      this !? AddSchedule(schedule) assert {
        case ScheduleAdded => succeed
        case other => fail(other.getClass)
      }
    }
  }
}
