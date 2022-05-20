package app.softnetwork.session.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.{Directive0, Directive1}
import com.softwaremill.session.RefreshTokenStorage
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import org.softnetwork.session.model.Session
import app.softnetwork.session.handlers.SessionRefreshTokenDao

import scala.concurrent.ExecutionContext

/**
  * Created by smanciot on 05/07/2018.
  */
trait SessionService {

  import Session._

  implicit def system: ActorSystem[_]

  implicit lazy val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(system)

  protected def sessionToDirective(session: Session)(implicit ec: ExecutionContext): Directive0 =
    setSession(if(session.refreshable)refreshable else oneOff, usingCookies, session)

  protected def _requiredSession(implicit ec: ExecutionContext): Directive1[Session] = requiredSession(refreshable, usingCookies)

  protected def _invalidateSession(implicit ec: ExecutionContext): Directive0 = invalidateSession(refreshable, usingCookies)

  protected def _optionalSession(implicit ec: ExecutionContext): Directive1[Option[Session]] = optionalSession(refreshable, usingCookies)
}
