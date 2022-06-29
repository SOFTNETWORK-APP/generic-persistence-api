package app.softnetwork.payment.launch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{Directives, Route}
import app.softnetwork.api.server.{ApiRoutes, DefaultComplete}
import app.softnetwork.api.server.launch.Application
import app.softnetwork.payment.handlers.MangoPayPaymentHandler
import app.softnetwork.payment.persistence.data.paymentKvDao
import app.softnetwork.payment.persistence.query.{GenericPaymentCommandProcessorStream, Scheduler2PaymentProcessorStream}
import app.softnetwork.payment.persistence.typed.MangoPayPaymentBehavior
import app.softnetwork.payment.service.MangoPayPaymentService
import app.softnetwork.persistence.jdbc.launch.PostgresGuardian
import app.softnetwork.persistence.jdbc.query.PostgresJournalProvider
import app.softnetwork.persistence.query.EventProcessorStream
import app.softnetwork.persistence.typed.{EntityBehavior, Singleton}
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import app.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior
import app.softnetwork.session.service.SessionService
import com.softwaremill.session.CsrfDirectives.{randomTokenCsrfProtection, setNewCsrfToken}
import com.softwaremill.session.CsrfOptions.checkHeader
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.Formats
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

object MangoPayApplication extends Application with ApiRoutes with PostgresGuardian with StrictLogging {
  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] =>  Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    MangoPayPaymentBehavior,
    SchedulerBehavior,
    SessionRefreshTokenBehavior
  )

  /**
    *
    * initialize all singletons
    */
  override def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq(paymentKvDao)

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys => Seq(
    new GenericPaymentCommandProcessorStream with MangoPayPaymentHandler with PostgresJournalProvider {
      override implicit def system: ActorSystem[_] = sys
    },
    new Scheduler2PaymentProcessorStream with MangoPayPaymentHandler with PostgresJournalProvider {
      override val tag: String = s"${MangoPayPaymentBehavior.persistenceId}-scheduler"
      override implicit def system: ActorSystem[_] = sys
    },
    new Entity2SchedulerProcessorStream() with SchedulerHandler with PostgresJournalProvider {
      override implicit def system: ActorSystem[_] = sys
    }
  )

  override def apiRoutes(system: ActorSystem[_]): Route =
    MangoPayPaymentService(system).route ~
      BasicServiceRoute(system).route

}

trait BasicServiceRoute extends SessionService with Directives with DefaultComplete with Json4sSupport {

  import app.softnetwork.persistence.generateUUID
  import app.softnetwork.serialization._

  import Session._

  implicit def formats: Formats = commonFormats

  implicit lazy val ec: ExecutionContext = system.executionContext

  val route: Route = {
    pathPrefix("auth") {
      basic
    }
  }

  lazy val basic: Route = path("basic"){
    get {
      // check anti CSRF token
      randomTokenCsrfProtection(checkHeader) {
        // check if a session exists
        _requiredSession(ec) { session =>
          complete(HttpResponse(StatusCodes.OK, entity = session.id))
        }
      }
    } ~ post
    {
      authenticateBasic("Basic Realm", BasicAuthAuthenticator) { identifier =>
        // create a new session
        val session = Session(generateUUID(identifier))
        sessionToDirective(session)(ec) {
          // create a new anti csrf token
          setNewCsrfToken(checkHeader) {
            complete(HttpResponse(StatusCodes.OK, entity = session.id))
          }
        }
      }
    } ~ delete
    {
      // check anti CSRF token
      randomTokenCsrfProtection(checkHeader) {
        // check if a session exists
        _requiredSession(ec) { _ =>
          // invalidate session
          _invalidateSession(ec) {
            complete(HttpResponse(StatusCodes.OK))
          }
        }
      }
    }
  }

  private def BasicAuthAuthenticator(credentials: Credentials): Option[String] = {
    credentials match {
      case p@Credentials.Provided(_) => Some(p.identifier)
      case _ => None
    }
  }

}

object BasicServiceRoute {
  def apply(_system: ActorSystem[_]): BasicServiceRoute = {
    new BasicServiceRoute {
      override implicit def system: ActorSystem[_] = _system
    }
  }
}