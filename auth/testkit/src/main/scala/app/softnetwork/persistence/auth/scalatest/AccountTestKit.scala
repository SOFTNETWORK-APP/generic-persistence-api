package app.softnetwork.persistence.auth.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.scalatest.NotificationTestKit
import app.softnetwork.persistence.auth.config.Settings
import app.softnetwork.persistence.auth.launch.AccountGuardian
import app.softnetwork.persistence.auth.model.{Account, AccountDecorator, Profile}
import org.scalatest.Suite

trait AccountTestKit[T <: Account with AccountDecorator, P <: Profile] extends AccountGuardian[T, P]
  with NotificationTestKit {_: Suite =>
  implicit lazy val tsystem: ActorSystem[_] = typedSystem()

  /**
    *
    * @return roles associated with this node
    */
  override def roles: Seq[String] = super.roles :+ Settings.AkkaNodeRole

}
