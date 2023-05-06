package app.softnetwork.persistence.schema

/** Created by smanciot on 12/05/2021.
  */
trait InMemorySchema extends Schema {
  override def schemaType: SchemaType = EmptySchema

  override def create(schema: String, separator: String): Unit = ()
}
