package app.softnetwork.persistence.jdbc.docker

import java.sql.DriverManager
import app.softnetwork.docker.DockerService
import com.whisk.docker._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PostgresService extends DockerService {

  import scala.concurrent.duration._
  def container = "postgres:9.6"
  lazy val containerPorts = Seq(5432)
  val PostgresUser: String = sys.env.getOrElse("POSTGRES_USER", "admin")
  val PostgresPassword: String = sys.env.getOrElse("POSTGRES_PASSWORD", "changeit")
  lazy val PostgresPort: Int = exposedPorts.toMap.get(5432).get.get
  import DockerService._
  lazy val PostgresHost: String = host()
  val PostgresDB: String = sys.env.getOrElse("POSTGRES_DB", PostgresUser)

  lazy val postgresContainer: DockerContainer = dockerContainer
    .withEnv(s"POSTGRES_USER=$PostgresUser", s"POSTGRES_PASSWORD=$PostgresPassword")
    .withReadyChecker(
      new PostgresReadyChecker(PostgresUser, PostgresPassword, None)
        .looped(15, 1.second)
    )

  import app.softnetwork.persistence._

  override val name: String = generateUUID()

  override def _container(): DockerContainer = postgresContainer

  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers
}

class PostgresReadyChecker(user: String, password: String, port: Option[Int] = None)
    extends DockerReadyChecker {

  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor,
                                                      ec: ExecutionContext): Future[Boolean] =
    container
      .getPorts()
      .map(ports =>
        Try {
          Class.forName("org.postgresql.Driver")
          val url = s"jdbc:postgresql://${docker.host}:${port.getOrElse(ports.values.head)}/"
          DriverManager.getConnection(url, user, password).close()
          true
        }.getOrElse(false)
      )
}
