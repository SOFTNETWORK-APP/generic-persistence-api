package akka.http.scaladsl.testkit

import app.softnetwork.api.server.scalatest.ServerTestKit
import app.softnetwork.api.server.{GrpcServer, GrpcServices}
import app.softnetwork.persistence.scalatest.PersistenceTestKit
import app.softnetwork.persistence.schema.Schema
import org.scalatest.Suite

/** Created by smanciot on 24/04/2020.
  */
trait PersistenceScalatestGrpcTest extends GrpcServer with ServerTestKit with PersistenceTestKit {
  _: Suite with GrpcServices with Schema =>

}
