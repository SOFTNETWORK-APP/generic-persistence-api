package app.softnetwork.persistence.auth.scalatest

import akka.http.scaladsl.testkit.PersistenceScalatestRouteTest
import app.softnetwork.persistence.auth.launch.AccountRoutes
import app.softnetwork.persistence.auth.model.{Account, AccountDecorator, Profile}
import org.scalatest.Suite

trait AccountRouteTestKit[T <: Account with AccountDecorator, P <: Profile] extends AccountRoutes[T, P] with PersistenceScalatestRouteTest with AccountTestKit[T, P] {
  _: Suite =>
}
