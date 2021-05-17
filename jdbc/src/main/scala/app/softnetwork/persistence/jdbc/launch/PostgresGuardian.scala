package app.softnetwork.persistence.jdbc.launch

import app.softnetwork.persistence.jdbc.query.PostgresSchemaProvider
import app.softnetwork.persistence.launch.PersistenceGuardian

/**
  * Created by smanciot on 07/05/2021.
  */
trait PostgresGuardian extends PersistenceGuardian with PostgresSchemaProvider
