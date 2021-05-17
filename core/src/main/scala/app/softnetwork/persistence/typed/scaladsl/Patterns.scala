package app.softnetwork.persistence.typed.scaladsl

import akka.actor.typed.{RecipientRef, Behavior, ActorSystem, ActorRef}
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import app.softnetwork.concurrent.Retryable
import app.softnetwork.config.Settings
import app.softnetwork.persistence.message.{EntityCommand, CommandWrapper, CommandResult, Command}
import app.softnetwork.persistence.model.Entity
import app.softnetwork.persistence.typed.CommandTypeKey
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.language.{postfixOps, implicitConversions}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 11/05/2021.
  */
trait Patterns[C <: Command, R <: CommandResult] extends Retryable[R] with StrictLogging {
  type Request = ActorRef[R] => C

  type Recipient

  implicit def command2Request(command: C) : Request

  implicit def timeout: Timeout = Settings.DefaultTimeout

  def recipientRef(recipient: Recipient)(implicit tTag: ClassTag[C], system: ActorSystem[_]): RecipientRef[C]

  def ?(recipient: Recipient, command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] =
    recipientRef(recipient) ? command

  def !(recipient: Recipient, command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit =
    recipientRef(recipient) ! command

  implicit def key2Recipient[T](key: T): Recipient

  protected def lookup[T](key: T)(implicit system: ActorSystem[_]): Future[Option[Recipient]] =
    Future.successful(Some(key))

  def ??[T](key: T, command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] = {
    implicit val ec = system.executionContext
    lookup(key) flatMap {
      case Some(recipient) => this ? (recipient, command)
      case _              => this ? (key, command)
    }
  }

  def ?![T](key: T, command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit = {
    implicit val ec = system.executionContext
    lookup(key) map {
      case Some(recipient) => this ! (recipient, command)
      case _              => this ! (key, command)
    }
  }

  def *?[T](keys: List[T], command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[List[R]] = {
    implicit val ec = system.executionContext
    Future.sequence(for(key <- keys) yield lookup(key) flatMap {
      case Some(recipient) => this ? (recipient, command)
      case _ => this ? (key, command)
    })
  }

  def *![T](keys: List[T], command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit = {
    implicit val ec = system.executionContext
    for(key <- keys) yield lookup(key) map {
      case Some(recipient) => this ! (recipient, command)
      case _ => this ? (key, command)
    }
  }

}

trait SingletonPattern[C <: Command, R <: CommandResult] extends Patterns[C, R] {

  protected def singleton: Recipient

  def ?(command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] = {
    recipientRef(singleton) ? command
  }

  def !(command: C)(implicit tTag: ClassTag[C], asystem: ActorSystem[_]): Unit = {
    recipientRef(singleton) ! command
  }

  override implicit def key2Recipient[T](key: T): Recipient = singleton
}

trait BehaviorPattern[C <: Command, R <: CommandResult] extends SingletonPattern[C, R] {
  type Recipient = Behavior[C]

  protected def name: String

  private[this] var ref: Option[RecipientRef[C]] = None

  override def recipientRef(recipient: Behavior[C])(implicit tTag: ClassTag[C], system: ActorSystem[_]): RecipientRef[C] ={
    if(ref.isEmpty){
      ref = Some(system.systemActorOf(singleton, name))
    }
    ref.get
  }

}

trait EntityPattern[C <: Command, R <: CommandResult] extends Patterns[C, R] with Entity {_: CommandTypeKey[C] =>

  type Recipient = String

  implicit def command2Request(command: C) : Request = replyTo => CommandWrapper(command, replyTo)

  implicit def key2Recipient[T](key: T): String = key match {
    case s: String => s
    case _         => key.toString
  }

  implicit class RecipientPattern(entityId: String) {
    def ref(implicit tTag: ClassTag[C], system: ActorSystem[_]): EntityRef[C] = {
      Try(ClusterSharding(system).entityRefFor(TypeKey, entityId)) match {
        case Success(s) => s
        case Failure(f) =>
          logger.error(
            s"""
               |Could not find entity for ${TypeKey.name}|$entityId
               |using ${system.path.toString}
               |""".stripMargin)
          throw f
      }
    }
  }

  override def recipientRef(recipient: String)(implicit tTag: ClassTag[C], system: ActorSystem[_]): RecipientRef[C] =
    recipient ref

  def !?(command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] =
    command match {
      case cmd: EntityCommand => this ? (cmd.id, command)
      case _ => this ? (ALL_KEY, command)
    }

  def !!(command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit =
    command match {
      case cmd: EntityCommand => this ! (cmd.id, command)
      case _ => this ! (ALL_KEY, command)
    }

}
