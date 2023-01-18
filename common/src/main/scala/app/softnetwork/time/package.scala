package app.softnetwork

import java.time.temporal.ChronoField
import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset, ZonedDateTime}
import java.util.Date
import scala.language.implicitConversions

/** Created by smanciot on 10/05/2021.
  */
package object time {

  def now(): ZonedDateTime = ZonedDateTime.now()

  implicit class DateExtensions(date: Date) {
    lazy val zdt: ZonedDateTime = ZonedDateTime.ofInstant(date.toInstant, ZoneOffset.UTC)
    lazy val toLocalDateTime: LocalDateTime = zdt.toLocalDateTime
    lazy val toLocalDate: LocalDate = zdt.toLocalDate
    lazy val toEpochSecond: Long = zdt.toEpochSecond
  }

  implicit def epochSecondToDate(epochSecond: Long): Date = {
    Date.from(Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toInstant)
  }

  implicit def epochSecondToLocalDate(epochSecond: Long): LocalDate = {
    Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toLocalDate
  }

  implicit def toLocalDate(d: Date): LocalDate = {
    d.toLocalDate
  }

  implicit class LocalDateExtensions(ld: LocalDate) {
    lazy val toDate: Date = Date.from(ld.atStartOfDay(ZoneOffset.UTC).toInstant)
  }

  implicit def toDate(ld: LocalDate): Date = {
    ld.toDate
  }

  implicit class LocalDateTimeExtensions(ldt: LocalDateTime) {
    lazy val toDate: Date = Date.from(ldt.atZone(ZoneOffset.UTC).toInstant)
  }

  implicit def toDate(ldt: LocalDateTime): Date = {
    ldt.toDate
  }

  def weekNumber(ld: LocalDate = LocalDate.now()): Int = {
    ld.get(ChronoField.ALIGNED_WEEK_OF_YEAR)
  }

  def bimonthlyNumber(ld: LocalDate = LocalDate.now()): Int = {
    periodicityNumber(ld, 2)
  }

  def quarterNumber(ld: LocalDate = LocalDate.now()): Int = {
    periodicityNumber(ld, 3)
  }

  def semesterNumber(ld: LocalDate = LocalDate.now()): Int = {
    periodicityNumber(ld, 6)
  }

  private[this] def periodicityNumber(ld: LocalDate = LocalDate.now(), numberOfMonths: Int) = {
    var periodicity = Math.floor(ld.getMonthValue / numberOfMonths).toInt
    if (ld.getMonthValue % numberOfMonths > 0) {
      periodicity += 1
    }
    periodicity
  }
}
