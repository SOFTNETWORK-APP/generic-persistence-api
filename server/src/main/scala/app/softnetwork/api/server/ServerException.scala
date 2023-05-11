package app.softnetwork.api.server

import akka.http.scaladsl.model.StatusCode

abstract class ServerException(message: String, val code: StatusCode) extends Exception(message)
