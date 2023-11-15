package app.softnetwork.session.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.launch.{PersistenceGuardian, PersistentEntity}
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.session.CsrfCheck
import app.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior

trait SessionGuardian extends PersistenceGuardian { _: SchemaProvider with CsrfCheck =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  def sessionEntities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = _ =>
    Seq(
      SessionRefreshTokenBehavior
    )

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = sessionEntities

}
