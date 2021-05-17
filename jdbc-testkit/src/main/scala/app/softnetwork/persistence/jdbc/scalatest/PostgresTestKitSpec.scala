package app.softnetwork.persistence.jdbc.scalatest

import org.scalatest.flatspec.AnyFlatSpec

class PostgresTestKitSpec extends AnyFlatSpec with PostgresTestKit {

  "postgres node" should "be ready with log line checker" in {
    waitForContainerUp()
  }
}