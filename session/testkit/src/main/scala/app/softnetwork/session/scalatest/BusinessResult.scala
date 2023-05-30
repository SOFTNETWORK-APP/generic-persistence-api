package app.softnetwork.session.scalatest

sealed trait BusinessResult

sealed trait BusinessError extends BusinessResult

case object NotFound extends BusinessError
