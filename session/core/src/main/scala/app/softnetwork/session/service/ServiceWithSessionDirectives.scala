package app.softnetwork.session.service

import app.softnetwork.api.server.ApiRoute
import app.softnetwork.persistence.message.{Command, CommandResult}
import app.softnetwork.persistence.service.Service
import app.softnetwork.persistence.typed.scaladsl.Patterns
import app.softnetwork.session.model.SessionData

trait ServiceWithSessionDirectives[C <: Command, R <: CommandResult, T <: SessionData]
    extends Service[C, R]
    with ApiRoute
    with SessionService[T] { _: Patterns[C, R] with SessionMaterials[T] => }
