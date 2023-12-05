package app.softnetwork.session.scalatest

import org.softnetwork.session.model.ApiKey

import scala.concurrent.Future

trait ApiKeyLoader {

  def loadApiKey(clientId: String): Future[Option[ApiKey]] = Future.successful(None)

}
