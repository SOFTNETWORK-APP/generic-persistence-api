package app.softnetwork.session.service

import app.softnetwork.api.server.ServiceEndpoints
import app.softnetwork.persistence.message.{Command, CommandResult}
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.session.{TapirCsrfCheckMode, TapirEndpoints, TapirSessionContinuity}
import com.softwaremill.session.{GetSessionTransport, SetSessionTransport}
import org.softnetwork.session.model.Session

trait ServiceWithSessionEndpoints[C <: Command, R <: CommandResult]
    extends ServiceEndpoints[C, R]
    with TapirEndpoints { _: EntityPattern[C, R] =>
  def sessionEndpoints: SessionEndpoints

  def sc: TapirSessionContinuity[Session] = sessionEndpoints.sc

  def st: SetSessionTransport = sessionEndpoints.st

  def gt: GetSessionTransport = sessionEndpoints.gt

  def checkMode: TapirCsrfCheckMode[Session] = sessionEndpoints.checkMode

}
