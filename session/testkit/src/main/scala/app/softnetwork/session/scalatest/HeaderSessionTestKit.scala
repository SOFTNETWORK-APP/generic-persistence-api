package app.softnetwork.session.scalatest

import akka.http.scaladsl.model.headers.RawHeader
import com.softwaremill.session.HeaderConfig
import org.scalatest.Suite

trait HeaderSessionTestKit extends SessionTestKit {
  _: Suite =>

  def headerConfig: HeaderConfig

  final override val sessionHeaderName: String = headerConfig.sendToClientHeaderName

  final override def mapRawHeader: RawHeader => Option[RawHeader] = raw =>
    if (raw.name == sessionHeaderName)
      Some(RawHeader(headerConfig.getFromClientHeaderName, raw.value))
    else
      Some(raw)
}
