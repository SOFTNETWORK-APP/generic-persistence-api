package app.softnetwork.scheduler.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.query.InMemoryJournalProvider
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.scheduler.config.Settings
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.launch.SchedulerGuardian
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream
import org.scalatest.Suite

trait SchedulerTestKit extends SchedulerGuardian with InMemoryPersistenceTestKit {_: Suite =>

  /**
    *
    * @return roles associated with this node
    */
  override def roles: Seq[String] = Seq(Settings.SchedulerConfig.akkaNodeRole)

  override def entity2SchedulerProcessorStream: ActorSystem[_] => Entity2SchedulerProcessorStream = sys =>
    new Entity2SchedulerProcessorStream with SchedulerHandler with InMemoryJournalProvider {
      override protected val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
      logger.info(tag)
    }

}
