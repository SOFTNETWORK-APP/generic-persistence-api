package app.softnetwork.persistence.schema
import akka.actor.typed.ActorSystem

trait InMemorySchemaProvider extends SchemaProvider {
  override def schema: ActorSystem[_] => Schema = _ => new InMemorySchema {}
}
