package app.softnetwork.notification.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.Scheduler2NotificationProcessorStream
import app.softnetwork.notification.peristence.typed.NotificationBehavior
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.{EventProcessorStream, SchemaProvider}
import app.softnetwork.scheduler.launch.SchedulerGuardian
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream

trait NotificationGuardian extends SchedulerGuardian {_: SchemaProvider =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  def notificationBehavior : ActorSystem[_] => NotificationBehavior[Notification]

  def notificationEntities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    Seq(
      notificationBehavior(sys)
    )

  /**
    * initialize all entities
    *
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    schedulerEntities(sys) ++ notificationEntities(sys)

  def scheduler2NotificationProcessorStream: ActorSystem[_] => Scheduler2NotificationProcessorStream

  override def scheduler2EntityProcessorStreams: ActorSystem[_] => Seq[Scheduler2EntityProcessorStream[_, _]] = sys =>
    Seq(
      scheduler2NotificationProcessorStream(sys)
    )

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    schedulerEventProcessorStreams(sys)

}
