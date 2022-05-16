package app.softnetwork.resource.handlers

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.resource.message.ResourceMessages._
import app.softnetwork.resource.persistence.typed.ResourceBehavior
import app.softnetwork.persistence.typed.CommandTypeKey

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Created by smanciot on 30/04/2020.
  */
trait ResourceTypeKey extends CommandTypeKey[ResourceCommand]{
  override def TypeKey(implicit tTag: ClassTag[ResourceCommand]): EntityTypeKey[ResourceCommand] = ResourceBehavior.TypeKey
}

trait ResourceHandler extends GenericResourceHandler with ResourceTypeKey

trait GenericResourceHandler extends EntityPattern[ResourceCommand, ResourceResult] {_: CommandTypeKey[ResourceCommand] =>
}
