package app.softnetwork.persistence.schema

/** Created by smanciot on 07/05/2021.
  */
trait Schema {
  def schemaType: SchemaType

  def initSchema(): Unit = {
    create(schemaType.schema)
  }

  def create(schema: String, separator: String = ";"): Unit

}
