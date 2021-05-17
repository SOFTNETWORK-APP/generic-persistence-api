package app.softnetwork.scheduler.message

import org.scalatest.wordspec.AnyWordSpecLike

/**
  * Created by smanciot on 03/05/2020.
  */
class CronTabCommandSpec extends AnyWordSpecLike {

  "Cron tab" must {
    "schedule timer every minute" in {
      assert(CronTabCommand("cron1", "* * * * *").next().get.toSeconds <= 60)
    }
  }
}
