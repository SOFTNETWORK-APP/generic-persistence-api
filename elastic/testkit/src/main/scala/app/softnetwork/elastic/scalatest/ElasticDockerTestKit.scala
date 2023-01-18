package app.softnetwork.elastic.scalatest

import java.net.{ServerSocket, URI}
import java.util.UUID
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import com.sksamuel.exts.Logging
import com.whisk.docker.DockerReadyChecker.LogLineContains
import com.whisk.docker.impl.dockerjava.DockerKitDockerJava
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{DockerContainer, LogLineReceiver}
import app.softnetwork.config.Settings._
import org.scalatest.Suite

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/** Created by smanciot on 28/06/2018.
  */
trait ElasticDockerTestKit
    extends DockerTestKit
    with DockerKitDockerJava
    with Logging
    with ElasticTestKit { _: Suite =>

  override lazy val client: ElasticClient = ElasticClient(ElasticProperties(elasticURL))

  override def beforeAll(): Unit = {
    super.beforeAll
    client.execute {
      createIndexTemplate("all_templates", "*").settings(
        Map("number_of_shards" -> 1, "number_of_replicas" -> 0)
      )
    } complete () match {
      case Success(_) => ()
      case Failure(f) => throw f
    }
  }

  override def afterAll(): Unit = {
    client.close()
    super.afterAll
  }

  import ElasticDockerTestKit._

  private def log(s: String): Unit = logger.warn(s)

  lazy val elasticHost: String = sys.env
    .get("ES_HOST")
    .flatMap { uri =>
      if (uri.startsWith("unix://")) {
        None
      } else Some(new URI(uri).getHost)
    }
    .getOrElse("127.0.0.1")

  lazy val elasticPort: Int = sys.env.get("ES_PORT").map(_.toInt).getOrElse(dynamicPort)

  lazy val elasticURL = s"http://$elasticHost:$elasticPort"

  lazy val clusterName: String = s"test-${UUID.randomUUID()}"

  override val StartContainersTimeout: FiniteDuration = DefaultTimeout

  override val StopContainersTimeout: FiniteDuration = DefaultTimeout

  val elasticsearchVersion = "6.7.2"

  lazy val elasticsearchContainer: DockerContainer =
    DockerContainer(s"docker.elastic.co/elasticsearch/elasticsearch:$elasticsearchVersion")
      .withEnv(
        "http.host=0.0.0.0",
        "xpack.graph.enabled=false",
        "xpack.ml.enabled=false",
        "xpack.monitoring.enabled=false",
        "xpack.security.enabled=false",
        "xpack.watcher.enabled=false",
        s"cluster.name=$clusterName",
        //      "script.inline=true",
        //      "script.stored=true",
        "discovery.type=single-node"
        //      "script.max_compilations_per_minute=60"
      )
      .withNetworkMode("bridge")
      .withPorts(9200 -> Some(elasticPort))
      .withReadyChecker(
        LogLineContains("started")
      )
      .withLogLineReceiver(LogLineReceiver(withErr = true, log))

  override lazy val dockerContainers: List[DockerContainer] =
    elasticsearchContainer :: super.dockerContainers
}

object ElasticDockerTestKit {
  def dynamicPort: Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }
}
