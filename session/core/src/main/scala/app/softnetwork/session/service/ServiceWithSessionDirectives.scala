package app.softnetwork.session.service

import app.softnetwork.api.server.ApiRoute
import app.softnetwork.persistence.message.{Command, CommandResult}
import app.softnetwork.persistence.service.Service
import app.softnetwork.persistence.typed.scaladsl.Patterns

trait ServiceWithSessionDirectives[C <: Command, R <: CommandResult]
    extends Service[C, R]
    with ApiRoute
    with SessionService { _: Patterns[C, R] with SessionMaterials => }
