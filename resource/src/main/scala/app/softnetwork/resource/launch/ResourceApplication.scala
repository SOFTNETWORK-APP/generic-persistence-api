package app.softnetwork.resource.launch

import app.softnetwork.persistence.launch.PersistenceApplication
import app.softnetwork.persistence.query.SchemaProvider

trait ResourceApplication extends PersistenceApplication with ResourceRoutes {_: SchemaProvider => }
