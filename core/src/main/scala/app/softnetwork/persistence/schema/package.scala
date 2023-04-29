package app.softnetwork.persistence

package object schema {

  sealed trait SchemaType { def schema: String }

  abstract class AbstractSchema(val schema: String) extends SchemaType

  case object EmptySchema extends AbstractSchema("")
}
