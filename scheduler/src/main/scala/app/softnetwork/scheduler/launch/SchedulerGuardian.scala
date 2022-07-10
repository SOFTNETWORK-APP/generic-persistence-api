package app.softnetwork.scheduler.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.launch.{PersistenceGuardian, PersistentEntity}
import app.softnetwork.persistence.query.{EventProcessorStream, SchemaProvider}
import app.softnetwork.scheduler.handlers.SchedulerDao
import app.softnetwork.scheduler.persistence.query.{Entity2SchedulerProcessorStream, Scheduler2EntityProcessorStream}
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

trait SchedulerGuardian extends PersistenceGuardian with StrictLogging {_: SchemaProvider =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  def schedulerEntities: ActorSystem[_] =>  Seq[PersistentEntity[_, _, _, _]] = _ => Seq(
    SchedulerBehavior
  )

  /**
    * initialize all entities
    *
    */
  override def entities: ActorSystem[_] =>  Seq[PersistentEntity[_, _, _, _]] = schedulerEntities

  def entity2SchedulerProcessorStream: ActorSystem[_] => Entity2SchedulerProcessorStream

  def scheduler2EntityProcessorStreams: ActorSystem[_] => Seq[Scheduler2EntityProcessorStream[_, _]]

  def schedulerEventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    Seq(
      entity2SchedulerProcessorStream(sys)
    ) ++ scheduler2EntityProcessorStreams(sys)

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = schedulerEventProcessorStreams

  def initSchedulerSystem: ActorSystem[_] => Unit = system => {
    Try(SchedulerDao.start(system)) match {
      case Success(_) =>
      case Failure(f) => logger.error(f.getMessage, f)
    }
  }

  override def initSystem: ActorSystem[_] => Unit = initSchedulerSystem
}
