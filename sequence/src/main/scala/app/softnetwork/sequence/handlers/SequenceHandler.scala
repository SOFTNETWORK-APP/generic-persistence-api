package app.softnetwork.sequence.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.sequence.message._
import app.softnetwork.sequence.persistence.typed.Sequence

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 07/04/2022.
  */
trait SequenceTypeKey extends CommandTypeKey[SequenceCommand]{
  override def TypeKey(implicit tTag: ClassTag[SequenceCommand]): EntityTypeKey[SequenceCommand] = Sequence.TypeKey
}

trait SequenceHandler extends EntityPattern[SequenceCommand, SequenceResult] with SequenceTypeKey{
  override val nbTries = 5
}

object SequenceHandler extends SequenceHandler

trait SequenceDao{_: SequenceHandler =>

  private var sequences: Map[String, Int] = Map.empty

  def inc(sequence: String)(implicit system: ActorSystem[_]): Future[Either[SequenceResult, Int]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val p = Promise[Either[SequenceResult, Int]]
    retry(!? (IncSequence(sequence))) onComplete {
      case Success(s) => s match {
        case r: SequenceIncremented =>
          sequences += (sequence -> r.value)
          p.success(Right(r.value))
        case other =>
          p.success(Left(other))
      }
      case Failure(_) =>
        val value = sequences.getOrElse(sequence, 0) + 1
        sequences += (sequence -> value)
        Try(!! (IncSequence(sequence)))
        p.success(Right(value))
    }
    p.future
  }

  def dec(sequence: String)(implicit system: ActorSystem[_]): Future[Either[SequenceResult, Int]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (DecSequence(sequence)).map {
      case r: SequenceDecremented => Right(r.value)
      case other => Left(other)
    }
  }

  def reset(sequence: String)(implicit system: ActorSystem[_]): Future[Either[SequenceResult, SequenceResetted]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (ResetSequence(sequence)).map {
      case r: SequenceResetted => Right(r)
      case other => Left(other)
    }
  }

  def load(sequence: String)(implicit system: ActorSystem[_]): Future[Either[SequenceResult, Int]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (LoadSequence(sequence)).map {
      case r: SequenceLoaded => Right(r.value)
      case other => Left(other)
    }
  }

}

object SequenceDao extends SequenceDao with SequenceHandler
