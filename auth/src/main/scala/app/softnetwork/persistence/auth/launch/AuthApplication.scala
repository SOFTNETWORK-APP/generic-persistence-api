package app.softnetwork.persistence.auth.launch

import akka.actor.typed.ActorSystem

import app.softnetwork.api.server.launch.Application
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.persistence.launch.PersistenceGuardian
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.scheduler.handlers.SchedulerDao
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior

import app.softnetwork.persistence.auth.config.Settings

import app.softnetwork.persistence.auth.handlers.BasicAccountDao

import app.softnetwork.persistence.auth.persistence.typed.BasicAccountBehavior

import app.softnetwork.persistence.auth.service.SecurityRoutes

import app.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior

import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 22/03/2018.
  */
trait AuthApplication extends Application with SecurityRoutes with PersistenceGuardian with StrictLogging {_: SchemaProvider =>

  override def behaviors: ActorSystem[_] =>  Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    BasicAccountBehavior,
    SessionRefreshTokenBehavior,
    SchedulerBehavior
  )

  override def initSystem: ActorSystem[_] => Unit = system => {
    val root = Settings.AdministratorsConfig.root
    BasicAccountDao.initAdminAccount(root.login, root.password)(system)
    Try(SchedulerDao.start(system)) match {
      case Success(_) =>
      case Failure(f) => logger.error(f.getMessage, f)
    }
  }
}
