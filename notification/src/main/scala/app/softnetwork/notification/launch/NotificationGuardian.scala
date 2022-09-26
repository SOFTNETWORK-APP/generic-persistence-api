package app.softnetwork.notification.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.{NotificationCommandProcessorStream, Scheduler2NotificationProcessorStream}
import app.softnetwork.notification.peristence.typed.NotificationBehavior
import app.softnetwork.persistence.launch.PersistentEntity
import app.softnetwork.persistence.query.{EventProcessorStream, SchemaProvider}
import app.softnetwork.scheduler.launch.SchedulerGuardian
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream

import scala.language.implicitConversions

trait NotificationGuardian extends SchedulerGuardian {_: SchemaProvider =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  def notificationBehavior : ActorSystem[_] => Option[NotificationBehavior[Notification]] = _ => None

  def notificationEntities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    notificationBehavior(sys) match {
      case Some(value) => Seq(value)
      case _ => Seq.empty
    }

  /**
    * initialize all entities
    *
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sys =>
    schedulerEntities(sys) ++ notificationEntities(sys)

  def scheduler2NotificationProcessorStream: ActorSystem[_] => Option[Scheduler2NotificationProcessorStream] = _ => None

  override def scheduler2EntityProcessorStreams: ActorSystem[_] => Seq[Scheduler2EntityProcessorStream[_, _]] = sys =>
    scheduler2NotificationProcessorStream(sys) match {
      case Some(value) => Seq(value)
      case _ => Seq.empty
    }

  def notificationCommandProcessorStream: ActorSystem[_] => Option[NotificationCommandProcessorStream] = _ => None

  def notificationEventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    notificationCommandProcessorStream(sys) match {
      case Some(value) => Seq(value)
      case _ => Seq.empty
    }

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    schedulerEventProcessorStreams(sys) ++
      notificationEventProcessorStreams(sys)

}
