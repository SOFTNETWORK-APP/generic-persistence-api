package app.softnetwork.persistence.typed.scaladsl

import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, RecipientRef}
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import app.softnetwork.concurrent.{Completion, Retryable}
import app.softnetwork.config.Settings
import app.softnetwork.persistence.message._
import app.softnetwork.persistence.model.Entity
import app.softnetwork.persistence.typed.{CommandTypeKey, Singleton}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import scala.language.reflectiveCalls

/** Created by smanciot on 11/05/2021.
  */
trait Patterns[C <: Command, R <: CommandResult] extends Retryable[R] { _: { def log: Logger } =>
  type Request = ActorRef[R] => C

  type Recipient

  implicit def command2Request(command: C): Request

  implicit def timeout: Timeout = Settings.DefaultTimeout

  def recipientRef(
    recipient: Recipient
  )(implicit tTag: ClassTag[C], system: ActorSystem[_]): RecipientRef[C]

  def ?(recipient: Recipient, command: C)(implicit
    tTag: ClassTag[C],
    system: ActorSystem[_]
  ): Future[R] =
    recipientRef(recipient) ? command

  def !(recipient: Recipient, command: C)(implicit
    tTag: ClassTag[C],
    system: ActorSystem[_]
  ): Unit =
    recipientRef(recipient) ! command

  implicit def key2Recipient[T](key: T): Recipient

  protected def lookup[T](key: T)(implicit system: ActorSystem[_]): Future[Option[Recipient]] =
    Future.successful(Some(key))

  def ??[T](key: T, command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    lookup(key) flatMap {
      case Some(recipient) => this ? (recipient, command)
      case _               => this ? (key, command)
    }
  }

  def ?![T](key: T, command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    lookup(key) map {
      case Some(recipient) => this ! (recipient, command)
      case _               => this ! (key, command)
    }
  }

  def *?[T](keys: List[T], command: C)(implicit
    tTag: ClassTag[C],
    system: ActorSystem[_]
  ): Future[List[R]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    Future.sequence(for (key <- keys) yield lookup(key) flatMap {
      case Some(recipient) => this ? (recipient, command)
      case _               => this ? (key, command)
    })
  }

  def *![T](keys: List[T], command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    for (key <- keys) yield lookup(key) map {
      case Some(recipient) => this ! (recipient, command)
      case _               => this ? (key, command)
    }
  }

}

trait CommandHandler[C <: Command, R <: CommandResult] {
  def handleCommand(command: C, replyTo: Option[ActorRef[R]])(implicit
    context: ActorContext[C]
  ): Unit = {}
}

trait SingletonPattern[C <: Command, R <: CommandResult]
    extends Patterns[C, R]
    with CommandHandler[C, R]
    with Singleton[C]
    with Completion { _: { def log: Logger } =>

  import akka.actor.typed.receptionist.Receptionist

  type Recipient = Behavior[C]

  type W = CommandWrapper[C, R] with C

  type WR = CommandWithReply[R] with C

  override def behavior: Behavior[C] =
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(key, context.self)
      Behaviors.receiveMessage {
        case w: W   => handleCommand(w.command, Some(w.replyTo))(context)
        case wr: WR => handleCommand(wr, Some(wr.replyTo))(context)
        case other  => handleCommand(other, None)(context)
      }
    }

  def ?(command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] = {
    actorRef ? command
  }

  def !(command: C)(implicit tTag: ClassTag[C], asystem: ActorSystem[_]): Unit = {
    actorRef ! command
  }

  final override implicit def key2Recipient[T](key: T): Recipient = behavior

  private[this] var maybeActorRef: Option[ActorRef[C]] = None

  case class SingletonRef(client: ActorRef[SingletonRefResult])

  case class SingletonRefResult(singletonRef: ActorRef[C])

  private[this] def supervisor: Behavior[SingletonRef] =
    Behaviors.setup[SingletonRef] { context =>
      context.log.info(s"spawn singleton [$name]")
      val child = context.spawn(behavior, name)
      context.system.receptionist ! Receptionist.Register(key, child)
      Behaviors.receiveMessage { message =>
        message.client ! SingletonRefResult(child)
        Behaviors.same
      }
    }

  private[this] def actorRef(implicit system: ActorSystem[_]): ActorRef[C] = {
    if (maybeActorRef.isEmpty) {
      val maybeSingletonRef = Option(singletonRef)
      if (maybeSingletonRef.isEmpty) {
        log.warn(s"actorRef for [$name] is undefined")
        system.receptionist ? Find(key) complete () match {
          case Success(s) => maybeActorRef = s.serviceInstances(key).headOption
          case Failure(f) =>
            log.error(f.getMessage, f)
        }
      } else {
        maybeActorRef = maybeSingletonRef
      }
    }
    maybeActorRef.getOrElse {
      log.info(s"spawn supervisor for singleton [$name]")
      import app.softnetwork.persistence._
      val supervisorRef = system.systemActorOf(supervisor, generateUUID())
      supervisorRef ? SingletonRef complete () match {
        case Success(s) =>
          maybeActorRef = Some(s.singletonRef)
          log.info(s"actorRef for [$name] has been loaded -> ${s.singletonRef.path}")
          s.singletonRef
        case Failure(f) =>
          log.error(f.getMessage, f)
          throw f
      }
    }
  }

  final override def recipientRef(
    recipient: Behavior[C]
  )(implicit tTag: ClassTag[C], system: ActorSystem[_]): RecipientRef[C] = actorRef
}

trait EntityPattern[C <: Command, R <: CommandResult] extends Patterns[C, R] with Entity {
  _: CommandTypeKey[C] =>

  def log: Logger

  type Recipient = String

  implicit def command2Request(command: C): Request = replyTo => CommandWrapper(command, replyTo)

  implicit def key2Recipient[T](key: T): String = key match {
    case s: String => s
    case _         => key.toString
  }

  implicit class RecipientPattern(entityId: String) {
    def ref(implicit tTag: ClassTag[C], system: ActorSystem[_]): EntityRef[C] = {
      Try(ClusterSharding(system).entityRefFor(TypeKey, entityId)) match {
        case Success(s) => s
        case Failure(f) =>
          log.error(s"""
               |Could not find entity for ${TypeKey.name}|$entityId
               |using ${system.path.toString}
               |""".stripMargin)
          throw f
      }
    }
  }

  override def recipientRef(
    recipient: String
  )(implicit tTag: ClassTag[C], system: ActorSystem[_]): RecipientRef[C] =
    recipient ref

  def !?(command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Future[R] =
    command match {
      case cmd: EntityCommand => this ? (cmd.id, command)
      case _                  => this ? (ALL_KEY, command)
    }

  def !!(command: C)(implicit tTag: ClassTag[C], system: ActorSystem[_]): Unit =
    command match {
      case cmd: EntityCommand => this ! (cmd.id, command)
      case _                  => this ! (ALL_KEY, command)
    }

}
