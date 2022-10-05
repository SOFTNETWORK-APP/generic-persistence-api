package app.softnetwork.persistence.auth.launch

import akka.actor.typed.ActorSystem
import app.softnetwork.api.server.GrpcServices
import app.softnetwork.api.server.launch.Application
import app.softnetwork.persistence.query.SchemaProvider
import app.softnetwork.persistence.auth.config.Settings
import app.softnetwork.persistence.auth.handlers.AccountDao
import app.softnetwork.persistence.auth.model.{Account, AccountDecorator, Profile}

/**
  * Created by smanciot on 22/03/2018.
  */
trait AccountApplication[T <: Account with AccountDecorator, P <: Profile] extends Application
  with AccountRoutes[T, P] with GrpcServices {self: SchemaProvider =>

  def accountDao: AccountDao

  def initAuthSystem: ActorSystem[_] => Unit = system => {
    val root = Settings.AdministratorsConfig.root
    accountDao.initAdminAccount(root.login, root.password)(system)
  }

  override def initSystem: ActorSystem[_] => Unit = system => {
    initAuthSystem(system)
    initSchedulerSystem(system)
  }
}
