package app.softnetwork.notification.peristence.typed

import java.util.Date
import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.typed.scaladsl.Effect
import app.softnetwork.notification.config.Settings
import org.slf4j.Logger
import org.softnetwork.akka.message.SchedulerEvents.SchedulerEventWithCommand
import app.softnetwork.scheduler.message.{AddSchedule, RemoveSchedule}
import org.softnetwork.akka.model.Schedule
import app.softnetwork.persistence.typed._
import app.softnetwork.notification.handlers._
import app.softnetwork.notification.message._
import app.softnetwork.notification.model._
import org.softnetwork.notification.message._
import org.softnetwork.notification.model._
import app.softnetwork.scheduler.config.{Settings => SchedulerSettings}

import scala.language.{implicitConversions, postfixOps}

/**
  * Created by smanciot on 13/04/2020.
  */
sealed trait NotificationBehavior[T <: Notification] extends EntityBehavior[
  NotificationCommand, T, NotificationEvent, NotificationCommandResult] { self: NotificationProvider[T] =>

  /**
    *
    * @return node role required to start this actor
    */
  override def role: String = Settings.NotificationConfig.akkaNodeRole

  private[this] val provider: NotificationProvider[T] = this

  private[this] val notificationTimerKey: String = "NotificationTimerKey"

  private[this] val delay = 1

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event    - the event to tag
    * @return event tags
    */
  override protected def tagEvent(entityId: String, event: NotificationEvent): Set[String] = {
    event match {
      case _: SchedulerEventWithCommand => Set(s"$persistenceId-to-scheduler", SchedulerSettings.SchedulerConfig.eventStreams.entityToSchedulerTag)
      case _ => Set(persistenceId)
    }
  }

  /**
    *
    * @param entityId - entity identity
    * @param state   - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @return effect
    */
  override def handleCommand( entityId: String,
                              state: Option[T],
                              command: NotificationCommand,
                              replyTo: Option[ActorRef[NotificationCommandResult]],
                              timers: TimerScheduler[NotificationCommand])(
    implicit context: ActorContext[NotificationCommand]): Effect[NotificationEvent, Option[T]] = {
    implicit val log: Logger = context.log
    implicit val system: ActorSystem[Nothing] = context.system

    command match {

      case cmd: AddNotification[T] =>
        import cmd._
        (notification match {
          case n: Mail => Some(MailRecordedEvent(n))
          case n: SMS => Some(SMSRecordedEvent(n))
          case n: Push => Some(PushRecordedEvent(n))
          case _ => None
        }) match {
          case Some(event) => Effect.persist(
            List(
              event,
              ScheduleForNotificationAdded(
                AddSchedule(
                  Schedule(persistenceId, entityId, notificationTimerKey, delay)
                )
              )
            )
          ).thenRun(_ => {
            NotificationAdded(entityId) ~> replyTo
          })
          case _ => Effect.unhandled
        }

      case _: RemoveNotification =>
        Effect.persist(
          List(
            NotificationRemovedEvent(
              entityId
            ),
            ScheduleForNotificationRemoved(
              RemoveSchedule(
                persistenceId,
                entityId,
                notificationTimerKey
              )
            )
          )
        ).thenRun(_ => {NotificationRemoved ~> replyTo})//.thenStop()

      case cmd: SendNotification[T] =>
        sendNotification(entityId, cmd.notification) match {
          case Some(event) =>
            import NotificationStatus._
            val notification = event.asInstanceOf[NotificationRecordedEvent[T]].notification
            import notification._
            Effect.persist(
              List(
                event,
                scheduledNotificationEvent(entityId, notification)
              )
            ).thenRun(_ => {
                status match {
                  case Rejected    => NotificationRejected(entityId)
                  case Undelivered => NotificationUndelivered(entityId)
                  case Sent        => NotificationSent(entityId)
                  case Delivered   => NotificationDelivered(entityId)
                  case _           => NotificationPending(entityId)
                }
              }
                ~> replyTo)
          case _ => Effect.none
        }

      case _: ResendNotification => state match {
        case Some(s) =>
          import s._
          import NotificationStatus._
          status match {
            case Sent      =>
              Effect.persist(
                ScheduleForNotificationRemoved(
                  RemoveSchedule(
                    persistenceId,
                    entityId,
                    notificationTimerKey
                  )
                )
              ).thenRun(_ => NotificationSent(entityId) ~> replyTo)
            case Delivered =>
              Effect.persist(
                ScheduleForNotificationRemoved(
                  RemoveSchedule(
                    persistenceId,
                    entityId,
                    notificationTimerKey
                  )
                )
              ).thenRun(_ => NotificationDelivered(entityId) ~> replyTo)
            case _         =>
              sendNotification(entityId, s) match {
                case Some(event) =>
                  import NotificationStatus._
                  val notification = event.asInstanceOf[NotificationRecordedEvent[T]].notification
                  import notification._
                  Effect.persist(
                    List(
                      event,
                      scheduledNotificationEvent(entityId, notification)
                    )
                  ).thenRun(_ => {
                      status match {
                        case Rejected    => NotificationRejected(entityId)
                        case Undelivered => NotificationUndelivered(entityId)
                        case Sent        => NotificationSent(entityId)
                        case Delivered   => NotificationDelivered(entityId)
                        case _           => NotificationPending(entityId)
                      }
                    } ~> replyTo)
                case _ => Effect.none
              }
          }
        case _ => Effect.none.thenRun(_ => NotificationNotFound ~> replyTo)
      }

      case _: GetNotificationStatus =>
        state match {
          case Some(s) =>
            import s._
            import NotificationStatus._
            status match {
              case Sent        => Effect.none.thenRun(_ => NotificationSent(entityId) ~> replyTo)
              case Delivered   => Effect.none.thenRun(_ => NotificationDelivered(entityId) ~> replyTo)
              case Rejected    => Effect.none.thenRun(_ => NotificationRejected(entityId) ~> replyTo)
              case Undelivered => Effect.none.thenRun(_ => NotificationUndelivered(entityId) ~> replyTo)
              case _           => ack(entityId, s, replyTo) // Pending
            }
          case _ => Effect.none.thenRun(_ => NotificationNotFound ~> replyTo)
        }

      case cmd: TriggerSchedule4Notification =>
        import cmd.schedule._
        if(key == notificationTimerKey){
          context.self ! ScheduleNotification
          Effect.none.thenRun(_ => Schedule4NotificationTriggered ~> replyTo)
        }
        else{
          Effect.none.thenRun(_ => Schedule4NotificationNotTriggered ~> replyTo)
        }

      case ScheduleNotification =>
        state match {
          case Some(s) =>
            import s._
            if(status.isSent || status.isDelivered){// the notification has been sent/delivered - the schedule should be removed
              Effect.persist(
                ScheduleForNotificationRemoved(
                  RemoveSchedule(
                    persistenceId,
                    entityId,
                    notificationTimerKey
                  )
                )
              ).thenNoReply()
            }
            else{// the notification is in pending, rejected or undelivered status ...
              sendNotification(entityId, s) match {
                case Some(event) =>
                  Effect.persist(
                    List(
                      event,
                      if (maxTries > 0 && nbTries < maxTries) {
                        ScheduleForNotificationAdded(
                          AddSchedule(
                            Schedule(persistenceId, entityId, notificationTimerKey, delay)
                          )
                        )
                      }
                      else {
                        ScheduleForNotificationRemoved(
                          RemoveSchedule(
                            persistenceId,
                            entityId,
                            notificationTimerKey
                          )
                        )
                      }
                    )
                  ).thenNoReply()
                case _ => Effect.persist(
                  ScheduleForNotificationAdded(
                    AddSchedule(
                      Schedule(persistenceId, entityId, notificationTimerKey, delay)
                    )
                  )
                ).thenNoReply()
              }
            }
          case _ => Effect.persist(
            ScheduleForNotificationRemoved(
              RemoveSchedule(
                persistenceId,
                entityId,
                notificationTimerKey
              )
            )
          ).thenNoReply()
        }

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[T], event: NotificationEvent)(
    implicit context: ActorContext[_]): Option[T] = {
    import context._
    event match {
      case evt: NotificationRecordedEvent[T] =>
        log.info(
          "Recording {}#{} in {} status with {} acknowledgment",
          persistenceId,
          evt.uuid,
          evt.notification.status.name,
          evt.notification.results
        )
        Some(evt.notification)

      case evt: NotificationRemovedEvent =>
        log.info(s"Removing $persistenceId#${evt.uuid}")
        emptyState

      case _  =>  super.handleEvent(state, event)
    }
  }

  private[this] def scheduledNotificationEvent(entityId: String, notification: T) = {
    import notification._
    if(status.isSent || status.isDelivered){// the notification has been sent/delivered - the schedule should be removed
      ScheduleForNotificationRemoved(
        RemoveSchedule(
          persistenceId,
          entityId,
          notificationTimerKey
        )
      )
    }
    else if (maxTries > 0 && nbTries < maxTries) {
      ScheduleForNotificationAdded(
        AddSchedule(
          Schedule(persistenceId, entityId, notificationTimerKey, delay)
        )
      )
    }
    else {
      ScheduleForNotificationRemoved(
        RemoveSchedule(
          persistenceId,
          entityId,
          notificationTimerKey
        )
      )
    }
  }

  private[this] def ack(
           _uuid: String,
           notification: T,
           replyTo: Option[ActorRef[NotificationCommandResult]])(implicit log: Logger, system: ActorSystem[_]
  ): Effect[NotificationEvent, Option[T]] = {
    import notification._
    val ack: NotificationAck = ackUuid match {
      case Some(_) =>
        import NotificationStatus._
        status match {
          case Pending =>
            log.info("Retrieving acknowledgement for {}#{} in {} status", persistenceId, _uuid, status.name)
            provider.ack(notification) // we only call the provider api if the notification is pending
          case _       => NotificationAck(ackUuid, results, new Date())
        }
      case _       => NotificationAck(None, results, new Date())
    }
    (notification match {
      case n: Mail =>
        Some(
          MailRecordedEvent(n.copyWithAck(ack).asInstanceOf[Mail])
        )
      case n: SMS =>
        Some(
          SMSRecordedEvent(n.copyWithAck(ack).asInstanceOf[SMS])
        )
      case n: Push =>
        Some(
          PushRecordedEvent(n.copyWithAck(ack).asInstanceOf[Push])
        )
      case _ => None
    }) match {
      case Some(event) =>
        Effect.persist(event)
          .thenRun(_ => {
            import NotificationStatus._
            ack.status match {
              case Rejected    => NotificationRejected(_uuid)
              case Undelivered => NotificationUndelivered(_uuid)
              case Sent        => NotificationSent(_uuid)
              case Delivered   => NotificationDelivered(_uuid)
              case _           => NotificationPending(_uuid)
            }
          }
        ~> replyTo)
      case _ => Effect.unhandled
    }
  }

  private[this] def sendNotification(entityId: String, notification: T)(implicit log: Logger, system: ActorSystem[_]
  ): Option[NotificationEvent] = {
    import notification._
    import NotificationStatus._
    val maybeSent = status match {
      case Sent        => None
      case Delivered   => None
      case Pending     =>
        notification.deferred match {
          case Some(deferred) if deferred.after(new Date()) => None
          case _ =>
            if(nbTries > 0) { // the notification has already been sent at least one time, waiting for an acknowledgement
              log.info("Retrieving acknowledgement for {}#{} in {} status", persistenceId, entityId, status.name)
              Some((provider.ack(notification), 0)) // FIXME acknowledgment must be properly implemented ...
            }
            else {
              log.info("Sending {}#{} in {} status to {} recipients", persistenceId, entityId, status.name, to.mkString(", "))
              Some((provider.send(notification), 1))
            }
        }
      case _ =>
        // Undelivered or Rejected
        if(maxTries > 0 && nbTries >= maxTries){
          None
        }
        else {
          log.info("Sending {}#{} in {} status to {} recipients", persistenceId, entityId, status.name, to.mkString(", "))
          Some((provider.send(notification), 1))
        }
    }
    notification match {
      case n: Mail =>
        Some(
          MailRecordedEvent(
            maybeSent match {
              case Some(ack) =>
                n.withNbTries(n.nbTries + ack._2).copyWithAck(ack._1).asInstanceOf[Mail]
              case _ => n
            }
          )
        )
      case n: SMS =>
        Some(
          SMSRecordedEvent(
            maybeSent match {
              case Some(ack) => n.withNbTries(n.nbTries + ack._2).copyWithAck(ack._1).asInstanceOf[SMS]
              case _ => n
            }
          )
        )
      case n: Push =>
        Some(
          PushRecordedEvent(
            maybeSent match {
              case Some(ack) => n.withNbTries(n.nbTries + ack._2).copyWithAck(ack._1).asInstanceOf[Push]
              case _ => n
            }
          )
        )
      case _ => None
    }
  }

}

trait AllNotificationsBehavior extends NotificationBehavior[Notification] with AllNotificationsProvider {
  override val persistenceId = "Notification"
}

trait MockAllNotificationsBehavior extends AllNotificationsBehavior with MockAllNotificationsProvider {
  override val persistenceId = "MockNotification"
}

object AllNotificationsBehavior extends AllNotificationsBehavior

object MockAllNotificationsBehavior extends MockAllNotificationsBehavior
