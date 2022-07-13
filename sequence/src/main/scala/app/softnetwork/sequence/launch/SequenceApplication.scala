package app.softnetwork.sequence.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.launch.Application
import app.softnetwork.persistence.launch.{PersistenceGuardian, PersistentEntity}
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.sequence.persistence.typed.Sequence
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 07/04/2022.
  */
trait SequenceApplication extends Application with SequenceRoutes with PersistenceGuardian with StrictLogging {_: SchemaProvider =>

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  override def entities: ActorSystem[_] =>  Seq[PersistentEntity[_, _, _, _]] = _ => Seq(Sequence)

}
