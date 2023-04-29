package app.softnetwork.persistence.schema

/** Created by smanciot on 12/05/2021.
  */
trait InMemorySchemaProvider extends SchemaProvider {
  override def schemaType: SchemaType = EmptySchema

  override def create(schema: String, separator: String): Unit = ()
}
