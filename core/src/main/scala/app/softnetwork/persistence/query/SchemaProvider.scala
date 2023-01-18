package app.softnetwork.persistence.query

/** Created by smanciot on 07/05/2021.
  */
trait SchemaProvider {
  def initSchema(): Unit
  def shutdown(): Unit = ()
}
