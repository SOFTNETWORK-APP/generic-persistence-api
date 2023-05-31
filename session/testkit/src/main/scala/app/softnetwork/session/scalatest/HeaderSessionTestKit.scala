package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.headers.RawHeader
import app.softnetwork.api.server.ApiRoutes
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite

trait HeaderSessionTestKit extends SessionTestKit {
  _: Suite with ApiRoutes =>

  def headerConfig: HeaderConfig

  final override val sessionHeaderName: String = headerConfig.sendToClientHeaderName

  final override def mapRawHeader: RawHeader => Option[RawHeader] = raw =>
    if (raw.name == sessionManager.config.sessionHeaderConfig.sendToClientHeaderName)
      Some(RawHeader(sessionManager.config.sessionHeaderConfig.getFromClientHeaderName, raw.value))
    else if (raw.name == sessionManager.config.refreshTokenHeaderConfig.sendToClientHeaderName)
      Some(
        RawHeader(sessionManager.config.refreshTokenHeaderConfig.getFromClientHeaderName, raw.value)
      )
    else
      Some(raw)
}
