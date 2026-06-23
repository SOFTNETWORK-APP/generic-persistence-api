package app.softnetwork.persistence.jdbc.db

import akka.actor
import app.softnetwork.persistence.jdbc.scalatest.H2TestKit
import com.typesafe.config.{Config, ConfigValueFactory}
import com.zaxxer.hikari.HikariDataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.lang.management.ManagementFactory
import javax.management.ObjectName

/** Regression test for issue #13: every `SlickDatabase` provider builds its own HikariCP pool, and
  * akka-persistence-jdbc names them all `slick.db`. In a single JVM that produces multiple pools
  * sharing one name, which collides on JMX registration (and breaks HikariCP's Prometheus tracker)
  * as soon as `registerMbeans = true`. The fix gives each provider a distinct pool name.
  */
class SlickDatabasePoolNameSpec extends AnyFlatSpec with Matchers with H2TestKit {

  /** A bare [[SlickDatabase]] provider sharing this suite's actor system and (optionally tweaked)
    * config — stands in for the many traits that mix [[SlickDatabase]] in.
    */
  private def newProvider(cfg: Config = config): SlickDatabase = {
    val sys = classicSystem
    new SlickDatabase {
      override implicit def classicSystem: actor.ActorSystem = sys
      override def config: Config = cfg
    }
  }

  private def poolNameOf(sd: SlickDatabase): String =
    sd.dataSource.asInstanceOf[HikariDataSource].getPoolName

  "SlickDatabase" should "assign a unique HikariCP pool name to each provider" in {
    val a = newProvider()
    val b = newProvider()
    try {
      val nameA = poolNameOf(a)
      val nameB = poolNameOf(b)
      nameA should startWith("slick.db-")
      nameB should startWith("slick.db-")
      nameA should not be nameB
    } finally {
      a.shutdown()
      b.shutdown()
    }
  }

  it should "leave an explicitly configured slick.db.poolName untouched" in {
    val cfg =
      config.withValue("slick.db.poolName", ConfigValueFactory.fromAnyRef("custom-pool"))
    val sd = newProvider(cfg)
    try poolNameOf(sd) shouldBe "custom-pool"
    finally sd.shutdown()
  }

  it should "register a distinct JMX MBean per pool when registerMbeans is enabled" in {
    val cfg =
      config.withValue("slick.db.registerMbeans", ConfigValueFactory.fromAnyRef(true))
    val a = newProvider(cfg)
    val b = newProvider(cfg)
    try {
      val server = ManagementFactory.getPlatformMBeanServer
      val mbeanA = new ObjectName(s"com.zaxxer.hikari:type=Pool (${poolNameOf(a)})")
      val mbeanB = new ObjectName(s"com.zaxxer.hikari:type=Pool (${poolNameOf(b)})")
      mbeanA should not be mbeanB
      server.isRegistered(mbeanA) shouldBe true
      server.isRegistered(mbeanB) shouldBe true
    } finally {
      a.shutdown()
      b.shutdown()
    }
  }
}
