package app.softnetwork.persistence.person

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.person.message.{PersonCommand, PersonCommandResult}
import app.softnetwork.persistence.person.typed.PersonBehavior
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.ClassTag

trait PersonHandler
    extends EntityPattern[PersonCommand, PersonCommandResult]
    with CommandTypeKey[PersonCommand] {

//  def log: Logger = LoggerFactory getLogger getClass.getName

  /** @param c
    *   - The type of commands to be send to this type of entity
    * @return
    *   A key that uniquely identifies the type of entity in the cluster
    */
  override def TypeKey(implicit c: ClassTag[PersonCommand]): EntityTypeKey[PersonCommand] =
    PersonBehavior.TypeKey
}

//object PersonHandler extends PersonHandler
