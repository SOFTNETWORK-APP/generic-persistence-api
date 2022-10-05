package akka.http.scaladsl.testkit

import app.softnetwork.api.server.{GrpcServer, GrpcServices}
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.persistence.scalatest.PersistenceTestKit
import org.scalatest.Suite

/**
  * Created by smanciot on 24/04/2020.
  *
  */
trait PersistenceScalatestGrpcTest extends GrpcServer with PersistenceTestKit {
  _: Suite with GrpcServices with SchemaProvider =>

  override lazy val interface: String = hostname

  override lazy val port: Int = {
    import java.net.ServerSocket
    new ServerSocket(0).getLocalPort
  }

}
