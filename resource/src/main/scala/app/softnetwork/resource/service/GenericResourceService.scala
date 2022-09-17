package app.softnetwork.resource.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import app.softnetwork.api.server._
import app.softnetwork.resource.config.Settings
import Settings._
import app.softnetwork.resource.handlers.{GenericResourceHandler, ResourceHandler}
import app.softnetwork.resource.message.ResourceMessages._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import org.softnetwork.session.model.Session
import app.softnetwork.persistence.service.Service
import app.softnetwork.resource.spi._
import app.softnetwork.serialization.commonFormats
import app.softnetwork.session.service.SessionService
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{Formats, jackson}
import org.json4s.jackson.Serialization

/**
  * Created by smanciot on 13/05/2020.
  */
trait GenericResourceService extends SessionService
  with Directives
  with DefaultComplete
  with Json4sSupport
  with StrictLogging
  with Service[ResourceCommand, ResourceResult] {_: GenericResourceHandler with ResourceProvider =>

  implicit def serialization: Serialization.type = jackson.Serialization

  implicit def formats: Formats = commonFormats

  import Session._

  val route: Route = {
    pathPrefix(Settings.ResourcePath){
      library ~ images ~ resource()
    }
  }

  lazy val images : Route = {
    pathPrefix("images"){
      resource("picture")
    }
  }

  lazy val library: Route = {
    pathPrefix("library"){
      path(Segments(1, 128)) { segments =>
        get {
          complete(HttpResponse(StatusCodes.OK, entity = listResources(segments.mkString("/"))))
        }
      }
    }
  }

  def resource(fieldName: String = "file"): Route = {
    path(Segments(2, 128)) { segments =>
      get {
        var uuid: String = segments.last
        var options: Seq[ResourceOption] = Seq.empty
        val uri: String = (ImageSizes.get(segments.last.toLowerCase()) match {
          case Some(value) =>
            options = Seq(SizeOption(value))
            uuid = segments(segments.size-2)
            segments.dropRight(2)
          case _ =>
            segments.dropRight(1)
        }).mkString("/")
        getResource(uuid, Some(uri), options)
      }
    } ~
      pathSuffix(Segment) { uuid =>
        get{
          getResource(uuid, None, Seq.empty)
        } ~
          // check anti CSRF token
          randomTokenCsrfProtection(checkHeader) {
            // check if a session exists
            _requiredSession(ec) { session =>
              post{
                extractRequestContext { ctx =>
                  implicit val materializer: Materializer = ctx.materializer
                  fileUpload(fieldName) {
                    case (_, byteSource) => completeResource(byteSource, s"${session.id}#$uuid")
                    case _ => complete(HttpResponse(StatusCodes.BadRequest))
                  }
                }
              } ~
                put {
                  extractRequestContext { ctx =>
                    implicit val materializer: Materializer = ctx.materializer
                    fileUpload(fieldName) {
                      case (_, byteSource) => completeResource(byteSource, s"${session.id}#$uuid", update = true)
                      case _ => complete(HttpResponse(StatusCodes.BadRequest))
                    }
                  }
                } ~
                delete {
                  run(session.id, DeleteResource(s"${session.id}#$uuid")) completeWith {
                    case ResourceDeleted => complete(HttpResponse(StatusCodes.OK))
                    case ResourceNotFound => complete(HttpResponse(StatusCodes.NotFound))
                    case r: ResourceError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
                    case _ => complete(HttpResponse(StatusCodes.BadRequest))
                  }
                }
            }
          }
      }
  }

  protected def getResource(uuid: String, uri: Option[String] = None, options: Seq[ResourceOption] = Seq.empty): Route = {
    loadResource(uuid, uri, None, options:_*) match {
      case Some(path) => getFromFile(path.toFile)
      case _ =>
        run(uuid, LoadResource(uuid)) match {
          case result: ResourceLoaded =>
            loadResource(uuid, uri, Option(result.resource.content), options:_*) match {
              case Some(path) => getFromFile(path.toFile)
              case _ => complete(HttpResponse(StatusCodes.NotFound))
            }
          case _ => complete(HttpResponse(StatusCodes.NotFound))
        }
    }
  }

  protected def completeResource(byteSource: Source[ByteString, Any], uuid: String, update: Boolean = false)(implicit materializer: Materializer): Route = {
    val future = byteSource.map { s => s.toArray}.runFold(Array[Byte]()){(acc,bytes) => acc ++ bytes}
    onSuccess(future){bytes =>
      logger.info(s"Resource $uuid uploaded successfully")
      run(
        uuid,
        if(update) {
          UpdateResource(
            uuid, bytes
          )
        }
        else{
          CreateResource(
            uuid, bytes
          )
        }
      ) completeWith {
        case ResourceCreated => complete(StatusCodes.Created)
        case ResourceUpdated => complete(StatusCodes.OK)
        case r: ResourceError => complete(HttpResponse(StatusCodes.BadRequest, entity = r))
        case _ => complete(HttpResponse(StatusCodes.BadRequest))
      }
    }
  }

}

trait LocalFileSystemGenericResourceService extends GenericResourceService with LocalFileSystemProvider{
  _: GenericResourceHandler =>
}

trait LocalFileSystemResourceService extends LocalFileSystemGenericResourceService with ResourceHandler

object LocalFileSystemResourceService {
  def apply(aSystem: ActorSystem[_]): LocalFileSystemResourceService =
    new LocalFileSystemResourceService {
      override implicit def system: ActorSystem[_] = aSystem
    }
}
