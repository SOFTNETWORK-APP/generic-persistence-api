package app.softnetwork.payment.persistence.query

import akka.actor.typed.eventstream.EventStream.Publish
import app.softnetwork.payment.handlers.GenericPaymentHandler
import app.softnetwork.payment.message.PaymentMessages.{PaymentCommand, PaymentResult, Schedule4PaymentTriggered, TriggerSchedule4Payment}
import app.softnetwork.persistence.query.JournalProvider
import app.softnetwork.scheduler.persistence.query.Scheduler2EntityProcessorStream
import org.softnetwork.akka.model.Schedule

import scala.concurrent.Future

case class ProbeSchedule4PaymentTriggered(entityId: String, key: String) extends PaymentResult

trait Scheduler2PaymentProcessorStream  extends Scheduler2EntityProcessorStream[PaymentCommand, PaymentResult] {
  _: GenericPaymentHandler with JournalProvider =>

  protected val forTests = false

  /**
    *
    * @param schedule - the schedule to trigger
    * @return true if the schedule has been successfully triggered, false otherwise
    */
  override protected def triggerSchedule(schedule: Schedule): Future[Boolean] = {
    !? (TriggerSchedule4Payment(schedule)) map {
      case Schedule4PaymentTriggered =>
        if(forTests){
          system.eventStream.tell(Publish(ProbeSchedule4PaymentTriggered(schedule.entityId, schedule.key)))
        }
        true
      case other =>
        if(forTests){
          system.eventStream.tell(Publish(other))
        }
        false
    }
  }
}
