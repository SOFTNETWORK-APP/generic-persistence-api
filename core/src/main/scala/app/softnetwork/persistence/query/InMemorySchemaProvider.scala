package app.softnetwork.persistence.query

/**
  * Created by smanciot on 12/05/2021.
  */
trait InMemorySchemaProvider extends SchemaProvider {
  override def initSchema(): Unit = ()
}
