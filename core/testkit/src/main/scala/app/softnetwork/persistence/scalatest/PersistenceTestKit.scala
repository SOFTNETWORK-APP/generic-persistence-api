package app.softnetwork.persistence.scalatest

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.MemberStatus
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import akka.cluster.typed.Cluster
import app.softnetwork.concurrent.scalatest.CompletionTestKit
import app.softnetwork.config.Settings
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.message.Command
import app.softnetwork.persistence.schema.{InMemorySchema, Schema, SchemaProvider}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.slf4j.Logger

import java.net.ServerSocket
import scala.language.implicitConversions
import scala.reflect.ClassTag

/** Created by smanciot on 04/01/2020.
  */
trait PersistenceTestKit
    extends PersistenceGuardian
    with BeforeAndAfterAll
    with Eventually
    with CompletionTestKit
    with Matchers
    with SchemaProvider {
  _: Suite with Schema =>

  import app.softnetwork.persistence._

  def log: Logger

  lazy val systemName: String = generateUUID()

  def hostname: String = "127.0.0.1"

  def dynamicPort: Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }

  private[this] var maybeManagementPort: Option[Int] = None

  def managementPort: Int = {
    maybeManagementPort match {
      case Some(port) => port
      case _ =>
        val port = dynamicPort
        maybeManagementPort = Some(port)
        port
    }
  }

  /** @return
    *   roles associated with this node
    */
  def roles: Seq[String] = Seq.empty

  final lazy val akka: String = s"""
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
                |clustering.cluster.name = $systemName
                |
                |akka.cluster.roles = [${roles.mkString(",")}]
                |
                |akka.discovery {
                |  config.services = {
                |    $systemName = {
                |      endpoints = [
                |        {
                |          host = "$hostname"
                |          port = $managementPort
                |        }
                |      ]
                |    }
                |  }
                |}
                |
                |akka.management {
                |  http {
                |    hostname = $hostname
                |    port     = $managementPort
                |  }
                |  cluster.bootstrap {
                |    contact-point-discovery {
                |      service-name = $systemName
                |    }
                |  }
                |}
                |
                |akka.remote.artery.canonical.hostname = $hostname
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
                |  expect-no-message-default = 1000ms
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
                |""".stripMargin + additionalConfig

  /** @return
    *   additional configuration
    */
  def additionalConfig: String = ""

  lazy val akkaConfig: Config = ConfigFactory.parseString(akka)

  lazy val config: Config = akkaConfig.withFallback(ConfigFactory.load())

  private[this] lazy val testKit = ActorTestKit(systemName, config)

  private[this] implicit lazy val system: ActorSystem[Nothing] = testKit.system

  def typedSystem(): ActorSystem[Nothing] = system

  /** `PatienceConfig` from [[_root_.akka.actor.testkit.typed.TestKitSettings#DefaultTimeout]]
    */
  implicit val patience: PatienceConfig =
    PatienceConfig(Settings.DefaultTimeout, Span(100, org.scalatest.time.Millis))

  override def schema: ActorSystem[_] => Schema = _ => this

  override def beforeAll(): Unit = {
    initAndJoinCluster()
  }

  override def afterAll(): Unit = {
    shutdownCluster()
  }

  /** init and join cluster
    */
  final def initAndJoinCluster(): Unit = {
    testKit.spawn(setup(), "guardian")
    // let the nodes join and become Up
    blockUntil("let the nodes join and become Up", 30, 2000)(() =>
      Cluster(system).selfMember.status == MemberStatus.Up
    )
  }

  final def shutdownCluster(): Unit = {
    testKit.shutdownTestKit()
  }

  def createTestProbe[M](): TestProbe[M] = testKit.createTestProbe()

  protected def subscribeProbe[T](probe: TestProbe[T])(implicit classTag: ClassTag[T]): Unit = {
    typedSystem().eventStream.tell(Subscribe(probe.ref))
  }

  def spawn[T](behavior: Behavior[T]): ActorRef[T] = testKit.spawn(behavior)

  def entityRefFor[R <: Command](typeKey: EntityTypeKey[R], entityId: String): EntityRef[R] =
    ClusterSharding(system).entityRefFor(typeKey, entityId)
}

trait InMemoryPersistenceTestKit extends PersistenceTestKit with InMemorySchema {
  _: Suite =>
  override lazy val config: Config =
    akkaConfig
      .withFallback(ConfigFactory.load("softnetwork-in-memory-persistence.conf"))
      .withFallback(ConfigFactory.load())

}
