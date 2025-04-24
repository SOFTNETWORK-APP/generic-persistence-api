package app.softnetwork.persistence.jdbc.scalatest

import akka.actor
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.schema.JdbcSchema
import app.softnetwork.persistence.scalatest.PersistenceTestKit
import app.softnetwork.persistence.typed._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite

/** Created by smanciot on 14/05/2021.
  */
trait JdbcPersistenceTestKit extends PersistenceTestKit with JdbcSchema { _: Suite =>

  override implicit lazy val classicSystem: actor.ActorSystem = typedSystem()

  def slickProfile: String

  def jdbcUrl: String

  def jdbcUser: String

  def jdbcPassword: String

  def jdbcDriver: String

  lazy val slick: String =
    s"""
       |slick {
       |  profile = "$slickProfile"
       |  db {
       |    url = "$jdbcUrl"
       |    user = "$jdbcUser"
       |    password = "$jdbcPassword"
       |    driver = "$jdbcDriver"
       |    numThreads = 100
       |    maxConnections = 100
       |    minConnections = 10
       |    idleTimeout = 10000 //10 seconds
       |  }
       |}
       |""".stripMargin

  override lazy val additionalConfig: String = slick + s"""
                         |akka-persistence-jdbc {
                         |  shared-databases {
                         |    $slick
                         |  }
                         |}
                         |
                         |jdbc-journal {
                         |  use-shared-db = "slick"
                         |  # circuit-breaker {
                         |  #   max-failures = 10
                         |  #   call-timeout = 10s
                         |  #   reset-timeout = 30s
                         |  # }
                         |}
                         |
                         |# the akka-persistence-snapshot-store in use
                         |jdbc-snapshot-store {
                         |  use-shared-db = "slick"
                         |  # circuit-breaker {
                         |  #   max-failures = 5
                         |  #   call-timeout = 20s
                         |  #   reset-timeout = 60s
                         |  # }
                         |}
                         |
                         |# the akka-persistence-query provider in use
                         |jdbc-read-journal {
                         |
                         |  refresh-interval = "100ms"
                         |  max-buffer-size = "500"
                         |
                         |  use-shared-db = "slick"
                         |}
                         |
                         |jdbc-durable-state-store {
                         |  use-shared-db = "slick"
                         |}
                         |
                         |jdbc-event-processor-offsets {
                         |  schema = "public"
                         |  table = "event_processor_offsets"
                         |}
                         |""".stripMargin

  override lazy val config: Config = {
    akkaConfig
      .withFallback(ConfigFactory.load("softnetwork-jdbc-persistence.conf"))
      .withFallback(ConfigFactory.load())
  }

}
