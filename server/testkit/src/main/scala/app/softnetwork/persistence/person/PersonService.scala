package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiEndpoint
import app.softnetwork.persistence.generateUUID
import app.softnetwork.persistence.person.message._
import app.softnetwork.persistence.service.Service
import org.json4s.Formats
import org.slf4j.{Logger, LoggerFactory}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.json4s.jsonBody
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

trait PersonService
    extends Service[PersonCommand, PersonCommandResult]
    with PersonHandler
    with ApiEndpoint {

  import app.softnetwork.serialization._

  implicit def formats: Formats = commonFormats

  val rootEndpoint: Endpoint[Unit, Unit, PersonCommandResult, Unit, Any] = endpoint
    .in("person")
    .errorOut(
      oneOf[PersonCommandResult](
        oneOfVariant[PersonNotFound.type](
          statusCode(StatusCode.NotFound).and(emptyOutputAs(PersonNotFound))
        )
      )
    )

  val addPersonEndpoint: ServerEndpoint[Any, Future] =
    rootEndpoint.post.in(jsonBody[AddPerson]).out(jsonBody[PersonAdded]).serverLogic { command =>
      run(generateUUID(), command).map {
        case r: PersonAdded => Right(r)
        case other          => Left(other)
      }
    }

  val updatePersonEndpoint: ServerEndpoint[Any, Future] =
    rootEndpoint.post
      .in(path[String]("uuid"))
      .in(jsonBody[UpdatePerson])
      .serverLogic { case (uuid, command) =>
        run(uuid, command).map {
          case PersonUpdated => Right(())
          case other         => Left(other)
        }
      }

  val updateNameEndpoint: ServerEndpoint[Any, Future] =
    rootEndpoint.post
      .in("name" / path[String]("uuid"))
      .in(jsonBody[UpdateName])
      .serverLogic { case (uuid, command) =>
        run(uuid, command).map {
          case NameUpdated => Right(())
          case other       => Left(other)
        }
      }

  val loadPersonEndpoint: ServerEndpoint[Any, Future] =
    rootEndpoint.get
      .in(path[String]("uuid"))
      .out(jsonBody[PersonLoaded])
      .serverLogic { uuid =>
        run(uuid, LoadPerson).map {
          case r: PersonLoaded => Right(r)
          case other           => Left(other)
        }
      }

  val deletePersonEndpoint: ServerEndpoint[Any, Future] =
    rootEndpoint.delete
      .in(path[String]("uuid"))
      .serverLogic { uuid =>
        run(uuid, DeletePerson).map {
          case PersonDeleted => Right(())
          case other         => Left(other)
        }
      }

  val endpoints: List[ServerEndpoint[Any, Future]] = List(
    addPersonEndpoint,
    updatePersonEndpoint,
    updateNameEndpoint,
    loadPersonEndpoint,
    deletePersonEndpoint
  )

  lazy val route: Route = apiRoute
}

object PersonService {
  def apply(_system: ActorSystem[_]): PersonService = new PersonService {
    lazy val log: Logger = LoggerFactory getLogger getClass.getName
    override implicit def system: ActorSystem[_] = _system
  }
}
