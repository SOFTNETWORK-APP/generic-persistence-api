package app.softnetwork

import java.time.ZonedDateTime

/**
  * Created by smanciot on 10/05/2021.
  */
package object time {

  def now(): ZonedDateTime = ZonedDateTime.now()

}
