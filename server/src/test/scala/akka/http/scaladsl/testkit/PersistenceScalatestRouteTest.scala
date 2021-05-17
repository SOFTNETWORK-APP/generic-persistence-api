package akka.http.scaladsl.testkit

import akka.actor.ActorSystem
import app.softnetwork.config.Settings
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.persistence.scalatest.{InMemoryPersistenceTestKit, PersistenceTestKit}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.Suite

/**
  * Created by smanciot on 24/04/2020.
  *
  */
trait PersistenceScalatestRouteTest extends PersistenceTestKit
  with RouteTest
  with TestFrameworkInterface
  with ScalatestUtils { this: Suite with SchemaProvider =>

  override protected def createActorSystem(): ActorSystem = {
    import app.softnetwork.persistence.typed._
    typedSystem()
  }

  implicit lazy val timeout: RouteTestTimeout = RouteTestTimeout(Settings.DefaultTimeout)

  def failTest(msg: String) = throw new TestFailedException(msg, 11)

}

trait InMemoryPersistenceScalatestRouteTest extends PersistenceScalatestRouteTest with InMemoryPersistenceTestKit {_: Suite =>
}