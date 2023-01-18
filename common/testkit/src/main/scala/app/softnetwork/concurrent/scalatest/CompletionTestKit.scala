package app.softnetwork.concurrent.scalatest

import app.softnetwork.concurrent.Completion
import com.typesafe.scalalogging.Logger
import org.scalatest._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/** Created by smanciot on 12/04/2021.
  */
trait CompletionTestKit extends Completion with Assertions {

  implicit class AwaitAssertion[T](future: Future[T])(implicit atMost: Duration = defaultTimeout) {
    def assert(fun: T => Assertion): Assertion =
      Try(Await.result(future, atMost)) match {
        case Success(s) => fun(s)
        case Failure(f) => fail(f.getMessage)
      }
  }

  implicit def toT[T](t: Try[T]): T = t match {
    case Success(s) => s
    case Failure(f) => fail(f.getMessage)
  }

  override implicit def toBoolean(t: Try[Boolean]): Boolean = toT[Boolean](t)

  override implicit def toOption[T](t: Try[Option[T]]): Option[T] = toT[Option[T]](t)

  override implicit def toSeq[T](t: Try[Seq[T]]): Seq[T] = toT[Seq[T]](t)

  def log: Logger = Logger(LoggerFactory.getLogger(getClass.getName))

  def blockUntil(explain: String, maxTries: Int = 20, sleep: Int = 1000)(
    predicate: () => Boolean
  ): Unit = {

    var tries = 0
    var done = false

    while (tries <= maxTries && !done) {
      if (tries > 0) Thread.sleep(sleep * tries)
      tries = tries + 1
      try {
        done = predicate()
      } catch {
        case e: Throwable =>
          log.warn(s"problem while testing predicate ${e.getMessage}")
      }
    }

    require(done, s"Failed waiting on: $explain")
  }

}
