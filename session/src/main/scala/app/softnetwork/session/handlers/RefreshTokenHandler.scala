package app.softnetwork.session.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData, RefreshTokenStorage}
import app.softnetwork.persistence.typed.CommandTypeKey
import org.softnetwork.session.model.Session
import app.softnetwork.session.message._
import app.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
  * Created by smanciot on 14/04/2020.
  */
trait RefreshTokenHandler[T] extends EntityPattern[RefreshTokenCommand, RefreshTokenResult]
  with RefreshTokenStorage[T]{_: CommandTypeKey[RefreshTokenCommand] =>
}

trait SessionRefreshTokenTypeKey extends CommandTypeKey[RefreshTokenCommand] {
  override def TypeKey(implicit tTag: ClassTag[RefreshTokenCommand]): EntityTypeKey[RefreshTokenCommand] =
    SessionRefreshTokenBehavior.TypeKey
}

trait SessionRefreshTokenHandler extends RefreshTokenHandler[Session] with SessionRefreshTokenTypeKey

trait RefreshTokenDao[T] extends RefreshTokenStorage[T]{_: RefreshTokenHandler[T] =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContext = system.executionContext

  override def lookup(selector: String): Future[Option[RefreshTokenLookupResult[T]]] = {
    logger.info(s"Looking up token for selector: $selector")
    this !? LookupRefreshToken(selector) map {
      case r: RefreshTokenResult => r.asInstanceOf[LookupRefreshTokenResult[T]].data
      case other => throw new Throwable(other.toString)
    }
  }

  override def store(data: RefreshTokenData[T]): Future[Unit] = {
    logger.info(s"Storing token for " +
      s"selector: ${data.selector}, " +
      s"user: ${data.forSession}, " +
      s"expires: ${data.expires}, " +
      s"now: ${System.currentTimeMillis()}"
    )
    this !? StoreRefreshToken(data) map {
      case r: RefreshTokenStored[_] => ()
      case other => throw new Throwable(other.toString)
    }
  }

  override def remove(selector: String): Future[Unit] = {
    logger.info(s"Removing token for selector: $selector")
    this !? RemoveRefreshToken(selector) map {
      case r: RefreshTokenRemoved => ()
      case other => throw new Throwable(other.toString)
    }
  }

  override def schedule[S](after: Duration)(op: => Future[S]): Unit = {
    logger.info("Running scheduled operation immediately")
    op
    Future.successful(())
  }
}

trait SessionRefreshTokenDao extends RefreshTokenDao[Session] with SessionRefreshTokenHandler

object SessionRefreshTokenDao {
  def apply(asystem: ActorSystem[_]): SessionRefreshTokenDao = {
    new SessionRefreshTokenDao(){
      override implicit val system = asystem
    }
  }
}
