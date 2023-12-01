package app.softnetwork.session.service

import app.softnetwork.api.server.ServiceEndpoints
import app.softnetwork.persistence.message.{Command, CommandResult}
import app.softnetwork.persistence.typed.scaladsl.Patterns
import app.softnetwork.session.model.SessionData

trait ServiceWithSessionEndpoints[C <: Command, R <: CommandResult, T <: SessionData]
    extends ServiceEndpoints[C, R]
    with SessionEndpoints[T] { _: Patterns[C, R] with SessionMaterials[T] => }
