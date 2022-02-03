package app.softnetwork.persistence.scalatest

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.MemberStatus
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import akka.cluster.typed.Cluster
import app.softnetwork.concurrent.scalatest.CompletionTestKit
import app.softnetwork.config.Settings
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.message.Command
import app.softnetwork.persistence.query.{InMemorySchemaProvider, SchemaProvider}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, Suite}

import java.net.{InetAddress, NetworkInterface}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.language.implicitConversions

/**
  * Created by smanciot on 04/01/2020.
  */
trait PersistenceTestKit extends PersistenceGuardian with BeforeAndAfterAll with Eventually with CompletionTestKit with Matchers {
  _: Suite with SchemaProvider =>

  import app.softnetwork.persistence._

  lazy val systemName: String = generateUUID()

  lazy val ipAddress: String = {
    NetworkInterface.getNetworkInterfaces.asScala.toSeq
      .filter(_.isUp)
      .filterNot(p => {
        val displayName = p.getDisplayName
        log.info(s"found $displayName network interface")
        displayName.toLowerCase.contains("docker")
      })
      .flatMap(p => p.getInetAddresses.asScala.toSeq)
      .find { address =>
        val host = address.getHostAddress
        host.contains(".") && !address.isLoopbackAddress && !address.isAnyLocalAddress && !address.isLinkLocalAddress
      }.getOrElse(InetAddress.getLocalHost).getHostAddress
  }

  lazy val akka = s"""
                |akka {
                |  stdout-loglevel = off // defaults to WARNING can be disabled with off. The stdout-loglevel is only in effect during system startup and shutdown
                |  log-dead-letters-during-shutdown = on
                |  loglevel = debug
                |  log-dead-letters = on
                |  log-config-on-start = off // Log the complete configuration at INFO level when the actor system is started
                |  loggers = ["akka.event.slf4j.Slf4jLogger"]
                |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
                |}
                |
                |akka.discovery {
                |  config.services = {
                |    $systemName = {
                |      endpoints = [
                |        {
                |          host = "$ipAddress"
                |          port = 8558
                |        }
                |      ]
                |    }
                |  }
                |}
                |
                |akka.management {
                |  http {
                |    # The hostname where the HTTP Server for Http Cluster Management will be started.
                |    # This defines the interface to use.
                |    # InetAddress.getLocalHost.getHostAddress is used not overriden or empty
                |    hostname = "$ipAddress"
                |
                |    # The port where the HTTP Server for Http Cluster Management will be bound.
                |    # The value will need to be from 0 to 65535.
                |    port = 8558 # port pun, it "complements" 2552 which is often used for Akka remoting
                |
                |    # Use this setting to bind a network interface to a different hostname or ip
                |    # than the HTTP Server for Http Cluster Management.
                |    # Use "0.0.0.0" to bind to all interfaces.
                |    # akka.management.http.hostname if empty
                |    bind-hostname = ""
                |
                |    # Use this setting to bind a network interface to a different port
                |    # than the HTTP Server for Http Cluster Management. This may be used
                |    # when running akka nodes in a separated networks (under NATs or docker containers).
                |    # Use 0 if you want a random available port.
                |    #
                |    # akka.management.http.port if empty
                |    bind-port = ""
                |
                |    # path prefix for all management routes, usually best to keep the default value here. If
                |    # specified, you'll want to use the same value for all nodes that use akka management so
                |    # that they can know which path to access each other on.
                |    base-path = ""
                |
                |    routes {
                |      health-checks = "akka.management.HealthCheckRoutes"
                |    }
                |
                |    # Should Management route providers only expose read only endpoints? It is up to each route provider
                |    # to adhere to this property
                |    route-providers-read-only = true
                |  }
                |
                |  # Health checks for readiness and liveness
                |  health-checks {
                |    # When exposting health checks via Akka Management, the path to expost readiness checks on
                |    readiness-path = "ready"
                |    # When exposting health checks via Akka Management, the path to expost readiness checks on
                |    liveness-path = "alive"
                |    # All readiness checks are executed in parallel and given this long before the check is timed out
                |    check-timeout = 1s
                |  }
                |
                |  cluster.bootstrap {
                |    contact-point-discovery {
                |      service-name = "$systemName"
                |      discovery-method = config
                |      required-contact-point-nr = 1
                |    }
                |  }
                |}
                |
                |akka.remote.artery.canonical.port = 0
                |
                |akka.coordinated-shutdown.exit-jvm = off
                |
                |akka.actor.testkit.typed {
                |  # Factor by which to scale timeouts during tests, e.g. to account for shared
                |  # build system load.
                |  timefactor =  1.0
                |
                |  # Duration to wait in expectMsg and friends outside of within() block
                |  # by default.
                |  # Dilated by the timefactor.
                |  single-expect-default = 10s
                |
                |  # Duration to wait in expectNoMessage by default.
                |  # Dilated by the timefactor.
                |  expect-no-message-default = 100ms
                |
                |  # The timeout that is used as an implicit Timeout.
                |  # Dilated by the timefactor.
                |  default-timeout = 5s
                |
                |  # Default timeout for shutting down the actor system (used when no explicit timeout specified).
                |  # Dilated by the timefactor.
                |  system-shutdown-default=60s
                |
                |  # Throw an exception on shutdown if the timeout is hit, if false an error is printed to stdout instead.
                |  throw-on-shutdown-timeout=false
                |
                |  # Duration to wait for all required logging events in LoggingTestKit.expect.
                |  # Dilated by the timefactor.
                |  filter-leeway = 3s
                |
                |}
                |
                |""".stripMargin

  lazy val akkaConfig = ConfigFactory.parseString(akka)

  lazy val config = akkaConfig.withFallback(ConfigFactory.load())

  private[this] lazy val testKit = ActorTestKit(systemName, config)

  private[this] implicit lazy val system: ActorSystem[Nothing] = testKit.system

  def typedSystem() = system

  /**
    * `PatienceConfig` from [[_root_.akka.actor.testkit.typed.TestKitSettings#DefaultTimeout]]
    */
  implicit val patience: PatienceConfig =
    PatienceConfig(Settings.DefaultTimeout, Span(100, org.scalatest.time.Millis))

  override def beforeAll(): Unit = {
    initAndJoinCluster()
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  /**
    * init and join cluster
    */
  final def initAndJoinCluster() = {
    testKit.spawn(setup(), "guardian")
    // let the nodes join and become Up
    blockUntil("let the nodes join and become Up", 30, 2000)(() => Cluster(system).selfMember.status == MemberStatus.Up)
  }

  def createTestProbe[M](): TestProbe[M] = testKit.createTestProbe()

  def spawn[T](behavior: Behavior[T]): ActorRef[T] = testKit.spawn(behavior)

  def entityRefFor[R <: Command](typeKey: EntityTypeKey[R], entityId: String): EntityRef[R] =
    ClusterSharding(system).entityRefFor(typeKey, entityId)
}

trait InMemoryPersistenceTestKit extends PersistenceTestKit with InMemorySchemaProvider { _: Suite =>
  override lazy val config =
    akkaConfig
      .withFallback(ConfigFactory.load("softnetwork-in-memory-persistence.conf"))
      .withFallback(ConfigFactory.load())

}