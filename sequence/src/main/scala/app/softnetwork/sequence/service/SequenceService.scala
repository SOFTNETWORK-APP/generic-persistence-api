package app.softnetwork.sequence.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.DefaultComplete
import app.softnetwork.persistence.service.Service
import app.softnetwork.sequence.handlers.{GenericSequenceHandler, SequenceHandler}
import app.softnetwork.sequence.message._
import app.softnetwork.serialization._
import org.json4s.Formats

/**
  * Created by smanciot on 15/05/2020.
  */
trait GenericSequenceService extends Service[SequenceCommand, SequenceResult] with Directives with DefaultComplete {
  _: GenericSequenceHandler =>

  implicit def formats: Formats = commonFormats

  val route: Route = {
    pathPrefix("sequence"){
      sequence
    }
  }

  lazy val sequence: Route = pathPrefix(Segment){ sequence =>
    pathEnd {
      post {
        run(sequence, IncSequence(sequence)) completeWith {
          case r: SequenceIncremented => complete(
            HttpResponse(
              StatusCodes.OK,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map(r.name -> r.value)
                )
              )
            )
          )
          case other => complete(
            HttpResponse(
              StatusCodes.InternalServerError,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map("error" -> other.getClass)
                )
              )
            )
          )
        }
      } ~
      get {
        run(sequence, LoadSequence(sequence)) completeWith {
          case r: SequenceLoaded => complete(
            HttpResponse(
              StatusCodes.OK,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map(r.name -> r.value)
                )
              )
            )
          )
          case other => complete(
            HttpResponse(
              StatusCodes.InternalServerError,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map("error" -> other.getClass)
                )
              )
            )
          )
        }
      }
    }
  }

}

trait SequenceService extends GenericSequenceService with SequenceHandler

object SequenceService{
  def apply(asystem: ActorSystem[_]): SequenceService = {
    new SequenceService {
      override implicit def system: ActorSystem[_] = asystem
    }
  }
}