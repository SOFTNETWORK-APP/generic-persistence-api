package app.softnetwork.notification.launch

import app.softnetwork.persistence.launch.PersistenceApplication
import app.softnetwork.persistence.query.SchemaProvider

trait NotificationApplication extends PersistenceApplication with NotificationGuardian {_: SchemaProvider => }
