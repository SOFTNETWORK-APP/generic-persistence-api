package app.softnetwork.persistence.jdbc.launch

import app.softnetwork.persistence.jdbc.schema.JdbcSchemaProvider
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.schema.SchemaType

/** Created by smanciot on 07/05/2021.
  */
trait JdbcPersistenceGuardian extends JdbcSchemaProvider { _: PersistenceGuardian =>
  def schemaType: SchemaType

}
