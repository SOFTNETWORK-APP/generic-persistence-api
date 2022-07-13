package app.softnetwork.notification.launch

import app.softnetwork.api.server.launch.HealthCheckApplication
import app.softnetwork.persistence.query.SchemaProvider

trait NotificationApplication extends HealthCheckApplication with NotificationGuardian {_: SchemaProvider => }
