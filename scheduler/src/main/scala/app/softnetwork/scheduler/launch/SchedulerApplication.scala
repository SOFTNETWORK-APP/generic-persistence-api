package app.softnetwork.scheduler.launch

import app.softnetwork.api.server.launch.HealthCheckApplication
import app.softnetwork.persistence.query.SchemaProvider

trait SchedulerApplication extends HealthCheckApplication with SchedulerGuardian {_: SchemaProvider => }
