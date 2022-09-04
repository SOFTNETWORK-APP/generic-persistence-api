package app.softnetwork.resource.scalatest

import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.resource.config.Settings
import app.softnetwork.resource.launch.GenericResourceGuardian
import app.softnetwork.resource.model.GenericResource
import org.scalatest.Suite

trait GenericResourceTestKit[Resource <: GenericResource] extends GenericResourceGuardian[Resource]
  with InMemoryPersistenceTestKit {_: Suite =>

  /**
    *
    * @return roles associated with this node
    */
  override def roles: Seq[String] = Seq(Settings.AkkaNodeRole)

}
