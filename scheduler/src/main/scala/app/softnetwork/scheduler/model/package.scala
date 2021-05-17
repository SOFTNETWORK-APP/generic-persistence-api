package app.softnetwork.scheduler

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Date

import com.markatta.akron.CronExpression
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 11/05/2021.
  */
package object model {

  trait SchedulerItem {
    def persistenceId: String
    def entityId: String
    def key: String
    val uuid = s"$persistenceId#$entityId#$key"
  }

  trait CronTabItem extends StrictLogging {
    def cron: String
    lazy val cronExpression = Try{CronExpression(cron)} match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage + s" -> [$cron]")
        CronExpression("*/5 * * * *") // By default every 5 minutes
    }
    def nextLocalDateTime(): Option[LocalDateTime] = {
      cronExpression.nextTriggerTime(LocalDateTime.now())
    }
    def next(from: Option[Date] = None): Option[FiniteDuration] = {
      (from match {
        case Some(s) => Some(new Timestamp(s.getTime).toLocalDateTime)
        case _ => nextLocalDateTime()
      }) match {
        case Some(ldt) =>
          val diff = LocalDateTime.now().until(ldt, ChronoUnit.SECONDS)
          if(diff < 0){
            Some(Math.max(1, 60 - Math.abs(diff)).seconds)
          }
          else{
            Some(diff.seconds)
          }

        case _ => None
      }
    }
  }
}
