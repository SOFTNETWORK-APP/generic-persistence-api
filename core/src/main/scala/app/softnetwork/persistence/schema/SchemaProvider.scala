package app.softnetwork.persistence.schema

import akka.actor.typed.ActorSystem

trait SchemaProvider {

  def schema: ActorSystem[_] => Schema

}
