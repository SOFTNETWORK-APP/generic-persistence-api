package app.softnetwork.api.server

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.serialization._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.Formats

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/** @author
  *   Valentin Kasas
  * @author
  *   smanciot
  */
trait DefaultComplete { this: Directives =>

  implicit def formats: Formats

  def handleCall[T](call: => T, handler: T => Route): Route = {
    Try(call) match {
      case Failure(t: ServerException) =>
        t.printStackTrace()
        complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Failure(t: Throwable) =>
        t.printStackTrace()
        complete(
          StatusCodes.InternalServerError -> Map(
            'type  -> t.getClass.getSimpleName,
            'error -> t.toString
          )
        )
      case Success(s) => handler(s)
    }
  }

  def handleComplete[T](call: Try[Try[T]], handler: T => Route): Route = {
    call match {
      case Failure(t) =>
        t.printStackTrace()
        complete(
          StatusCodes.InternalServerError -> Map(
            'type  -> t.getClass.getSimpleName,
            'error -> t.toString
          )
        )
      case Success(res) =>
        res match {
          case Success(id) => handler(id)
          case Failure(t: ServerException) =>
            t.printStackTrace()
            complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
          case Failure(t) =>
            t.printStackTrace()
            complete(
              StatusCodes.InternalServerError -> Map(
                'type  -> t.getClass.getSimpleName,
                'error -> t.toString
              )
            )
        }
    }
  }

  implicit class CompleteWith[T](future: Future[T]) {
    def completeWith(fun: T => Route): Route =
      onComplete(future) {
        case Success(s) => fun(s)
        case Failure(f) =>
          complete(
            HttpResponse(
              StatusCodes.InternalServerError,
              entity = s"An error occurred: ${f.getMessage}"
            )
          )
      }
  }

}
