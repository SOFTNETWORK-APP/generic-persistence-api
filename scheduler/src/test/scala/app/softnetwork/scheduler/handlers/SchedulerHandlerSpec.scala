package app.softnetwork.scheduler.handlers

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.scheduler.message.{CronTabAdded, LoadScheduler, SchedulerLoaded}
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.akka.message.scheduler._
import org.softnetwork.akka.model.CronTab

/**
  * Created by smanciot on 19/03/2020.
  */
class SchedulerHandlerSpec extends SchedulerHandler with AnyWordSpecLike with InMemoryPersistenceTestKit {

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: (ActorSystem[_]) => Seq[EntityBehavior[_, _, _, _]] = system => List(
    SchedulerBehavior
  )

  implicit lazy val system = typedSystem()

  implicit lazy val ec = system.executionContext

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
        case r: SchedulerLoaded => assert(r.schdeduler.cronTabs.exists(ct => ct.uuid == cronTab.uuid))
        case _ => fail()
      }
    }
  }
}
