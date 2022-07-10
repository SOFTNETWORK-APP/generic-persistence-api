package app.softnetwork.persistence.auth.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.persistence.auth.model.{Account, AccountDecorator, Profile}
import app.softnetwork.persistence.auth.serialization.authFormats
import app.softnetwork.persistence.auth.service.AccountService
import app.softnetwork.persistence.query.SchemaProvider
import org.json4s.Formats

trait AccountRoutes[T <: Account with AccountDecorator, P <: Profile] extends ApiRoutes with AccountGuardian[T, P] {
  _: SchemaProvider =>

  override implicit def formats: Formats = authFormats

  def accountService: ActorSystem[_] => AccountService

  override def apiRoutes(system: ActorSystem[_]): Route = accountService(system).route

}
