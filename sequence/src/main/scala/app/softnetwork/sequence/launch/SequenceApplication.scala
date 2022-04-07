package app.softnetwork.sequence.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.launch.PostgresGuardian
import app.softnetwork.api.server.launch.Application
import app.softnetwork.persistence.typed._
import app.softnetwork.scheduler.handlers.SchedulerDao
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import app.softnetwork.sequence.persistence.typed.Sequence
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 07/04/2022.
  */
object SequenceApplication extends Application with SequenceRoutes with PostgresGuardian with StrictLogging {

  override def behaviors: ActorSystem[_] =>  Seq[EntityBehavior[_, _, _, _]] = _ => Seq(Sequence, SchedulerBehavior)

  override def initSystem: ActorSystem[_] => Unit = system => {
    Try(SchedulerDao.start(system)) match {
      case Success(_) =>
      case Failure(f) => logger.error(f.getMessage, f)
    }
  }
}
