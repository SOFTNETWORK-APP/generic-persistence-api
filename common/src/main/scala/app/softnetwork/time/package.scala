package app.softnetwork

import java.time.temporal.ChronoField
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.Date
import scala.language.implicitConversions

/** Created by smanciot on 10/05/2021.
  */
package object time {

  implicit def zoneId: ZoneId = ZoneId.systemDefault()

  implicit class DateExtensions(d: Date) {
    def toLocalDate: LocalDate = d
    def toLocalDateTime: LocalDateTime = d
    def toZonedDateTime: ZonedDateTime = d
    def toEpochSecond: Long = d.toInstant.getEpochSecond
  }

  implicit def epochSecondToInstant(epochSecond: Long): Instant = {
    Instant.ofEpochSecond(epochSecond)
  }

  implicit def dateToInstant(date: Date): Instant = {
    date.toInstant
  }

  implicit def instantToDate(instant: Instant): Date = {
    Date.from(instant)
  }

  implicit def epochSecondToDate(epochSecond: Long): Date = {
    Date.from(epochSecond)
  }

  implicit class LocalDateExtensions(ld: LocalDate) {
    def toDate: Date = ld
  }

  implicit def localDateToDate(ld: LocalDate): Date = {
    Date.from(ld.atStartOfDay(zoneId).toInstant)
  }

  implicit class LocalDateTimeExtensions(ldt: LocalDateTime) {
    def toDate: Date = ldt
  }

  implicit def localDateTimeToDate(ldt: LocalDateTime): Date = {
    Date.from(ldt.atZone(zoneId).toInstant)
  }

  implicit class ZonedDateTimeExtensions(zdt: ZonedDateTime) {
    def toDate: Date = zdt
  }

  implicit def zonedDateTimeToDate(zdt: ZonedDateTime): Date = {
    Date.from(zdt.toInstant)
  }

  implicit def epochSecondToLocalDate(epochSecond: Long): LocalDate = {
    Instant.ofEpochSecond(epochSecond).atZone(zoneId).toLocalDate
  }

  implicit def dateToEpochSecond(d: Date): Long = {
    d.toInstant.getEpochSecond
  }

  implicit def dateToLocalDate(d: Date): LocalDate = {
    ZonedDateTime.ofInstant(d.toInstant, zoneId).toLocalDate
  }

  implicit def dateToLocalDateTime(d: Date): LocalDateTime = {
    ZonedDateTime.ofInstant(d.toInstant, zoneId).toLocalDateTime
  }

  implicit def dateToZonedDateTime(d: Date): ZonedDateTime = {
    ZonedDateTime.ofInstant(d.toInstant, zoneId)
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
