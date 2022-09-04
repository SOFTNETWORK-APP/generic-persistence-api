package app.softnetwork.resource.launch

import app.softnetwork.persistence.launch.PersistenceApplication
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.resource.model.GenericResource

trait GenericResourceApplication[Resource <: GenericResource] extends PersistenceApplication
  with GenericResourceRoutes[Resource] {_: SchemaProvider => }
