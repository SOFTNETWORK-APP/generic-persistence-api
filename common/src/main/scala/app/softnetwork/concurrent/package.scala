package app.softnetwork

import app.softnetwork.config.Settings

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

package object concurrent {

  /**
    * Created by smanciot on 06/05/2021.
    */
  trait Completion {

    /**
      *
      * maximum wait time, which may be negative (no waiting is done),
      * [[scala.concurrent.duration.Duration.Inf Duration.Inf]] for unbounded waiting, or a finite positive duration
      */
    def defaultTimeout: FiniteDuration = Settings.DefaultTimeout

    implicit class AwaitCompletion[T](future: Future[T])(implicit atMost: Duration = defaultTimeout){
      /**
        * Usage:
        * aFuture wait {
        *   case a: A => //...
        *   case other => //...
        * } match {
        *   case Success(s) => s
        *   case Failure(f) => //...
        * }
        *
        * @param fun - the function which will be called
        * @tparam B - the function return type
        * @return a successfull B or an exception
        */
      def await[B](fun: T => B): Try[B] =
        Try(Await.result(future, atMost)) match {
          case Success(s) => Success(fun(s))
          case Failure(f) => Failure(f)
        }
      def complete(): Try[T] = await[T]({t => t})
    }

    implicit def toBoolean(t: Try[Boolean]): Boolean = t match {
      case Success(s) => s
      case Failure(f) => false
    }

    implicit def toSeq[T](t: Try[Seq[T]]): Seq[T] = t match {
      case Success(s) => s
      case Failure(f) => Seq.empty
    }

    implicit def toOption[T](t: Try[Option[T]]): Option[T] = t match {
      case Success(s) => s
      case Failure(f) => None
    }

  }

  object Completion extends Completion

  /**
    * Created by smanciot on 10/05/2021.
    */
  trait Retryable[T] {

    def nbTries = 1

    def retry(fn: => Future[T])(implicit ec: ExecutionContext): Future[T] = retry(nbTries)(fn)

    def retry(n: Int)(fn: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
      val p = Promise[T]
      fn onComplete {
        case Success(x) => p.success(x)
        case _ if n > 1 => p.completeWith(retry(n - 1)(fn))
        case Failure(f) => p.failure(f)
      }
      p.future
    }
  }
}
