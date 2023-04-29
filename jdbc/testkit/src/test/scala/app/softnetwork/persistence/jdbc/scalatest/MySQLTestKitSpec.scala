package app.softnetwork.persistence.jdbc.scalatest

import org.scalatest.flatspec.AnyFlatSpec

class MySQLTestKitSpec extends AnyFlatSpec with MySQLTestKit {

  "mysql node" should "be running" in {
    assert(jdbcContainer.isRunning)
  }
}
