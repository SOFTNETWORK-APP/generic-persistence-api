package app.softnetwork.session.service

import app.softnetwork.api.server.ServiceEndpoints
import app.softnetwork.persistence.message.{Command, CommandResult}
import app.softnetwork.persistence.typed.scaladsl.Patterns

trait ServiceWithSessionEndpoints[C <: Command, R <: CommandResult]
    extends ServiceEndpoints[C, R]
    with SessionEndpoints { _: Patterns[C, R] with SessionMaterials => }
