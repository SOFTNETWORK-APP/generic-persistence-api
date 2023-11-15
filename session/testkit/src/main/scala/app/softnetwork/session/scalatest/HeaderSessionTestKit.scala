package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.headers.RawHeader
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.session.service.SessionMaterials
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite

trait HeaderSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes with SessionMaterials =>

  def headerConfig: HeaderConfig

  final override val sessionHeaderName: String = headerConfig.sendToClientHeaderName

  final override def mapRawHeader: RawHeader => Option[RawHeader] = raw =>
    if (raw.name == manager.config.sessionHeaderConfig.sendToClientHeaderName)
      Some(RawHeader(manager.config.sessionHeaderConfig.getFromClientHeaderName, raw.value))
    else if (raw.name == manager.config.refreshTokenHeaderConfig.sendToClientHeaderName)
      Some(
        RawHeader(manager.config.refreshTokenHeaderConfig.getFromClientHeaderName, raw.value)
      )
    else
      Some(raw)
}
