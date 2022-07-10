package app.softnetwork.scheduler.launch

import app.softnetwork.persistence.launch.PersistenceApplication
import app.softnetwork.persistence.query.SchemaProvider

trait SchedulerApplication extends PersistenceApplication with SchedulerGuardian {_: SchemaProvider => }
