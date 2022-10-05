package app.softnetwork.persistence.auth.scalatest

import akka.http.scaladsl.testkit.PersistenceScalatestRouteTest
import app.softnetwork.api.server.GrpcServices
import app.softnetwork.persistence.auth.launch.AccountRoutes
import app.softnetwork.persistence.auth.model.{Account, AccountDecorator, Profile}
import org.scalatest.Suite

trait AccountRouteTestKit[T <: Account with AccountDecorator, P <: Profile] extends PersistenceScalatestRouteTest
  with AccountRoutes[T, P] with GrpcServices with AccountTestKit[T, P] {_: Suite =>
}
