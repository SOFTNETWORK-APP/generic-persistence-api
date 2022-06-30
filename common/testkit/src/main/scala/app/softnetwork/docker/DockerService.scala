package app.softnetwork.docker

import app.softnetwork.concurrent.scalatest.CompletionTestKit
import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker._

import java.net.URI

import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.concurrent.Eventually
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Await
import scala.concurrent.duration._

trait DockerService extends TestSuite
  with Matchers
  with DockerTestKit
  with Eventually
  with CompletionTestKit {

  override val StartContainersTimeout: FiniteDuration = defaultTimeout

  override val StopContainersTimeout: FiniteDuration = defaultTimeout

  def container: String

  def containerPorts: Seq[Int]

  def name: String = container.split(":").head

  def containerEnv: Map[String, String] = sys.env.filterKeys(_.startsWith(s"${name.toUpperCase}_"))

  import DockerService._

  def exposedPort(port: Int): Option[Int] = Some(
        containerEnv.getOrElse(s"${name.toUpperCase}_$port", dynamicPort().toString).toInt
      )

  lazy val exposedPorts: Seq[(Int, Option[Int])] = containerPorts.map{ port => (port, exposedPort(port))}

  lazy val dockerContainer: DockerContainer = DockerContainer(container, Some(name))
    .withPorts(exposedPorts:_*)
    .withEnv(
      containerEnv.map( t => s"${t._1}=${t._2}").toSeq:_*
    )

  def _container(): DockerContainer

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(
    DefaultDockerClient.fromEnv().build())

  protected def waitForContainerUp(maxTries: Int = (defaultTimeout.toMillis/1000).toInt, sleep: Int = 1000): Unit = {
    val predicate: () => Boolean = () => Await.result(
      isContainerReady(_container()),
      sleep.milliseconds
    )
    blockUntil(s"container $container is up", maxTries, sleep)(predicate)
  }

}

object DockerService {

  import java.net.ServerSocket

  def dynamicPort(): Int = {
    val socket = new ServerSocket(0)
    val port   = socket.getLocalPort
    socket.close()
    port
  }

  def host(): String = sys.env
    .get("DOCKER_HOST")
    .flatMap { uri =>
      if (uri.startsWith("unix://")) {
        None
      } else {
          Some(new URI(uri).getHost)
      }
    }
    .getOrElse("127.0.0.1")

}
