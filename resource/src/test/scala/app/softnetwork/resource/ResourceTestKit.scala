package app.softnetwork.resource

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream.Subscribe
import app.softnetwork.persistence.query.{EventProcessorStream, InMemoryJournalProvider}
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.{EntityBehavior, Singleton}
import app.softnetwork.resource.persistence.query.ResourceToLocalFileSystemProcessorStream
import app.softnetwork.resource.persistence.typed.ResourceBehavior
import org.scalatest.Suite

import scala.reflect.ClassTag

trait ResourceTestKit extends InMemoryPersistenceTestKit {_: Suite =>

  implicit lazy val tsystem: ActorSystem[_] = typedSystem()

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    ResourceBehavior
  )

  /**
    *
    * initialize all singletons
    */
  override def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq.empty

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    Seq(
      new ResourceToLocalFileSystemProcessorStream with InMemoryJournalProvider {
        override val forTests = true
        override implicit def system: ActorSystem[_] = sys
      }
    )

  protected def subscribeProbe[T](probe: TestProbe[T])(implicit classTag: ClassTag[T]): Unit = {
    typedSystem().eventStream.tell(Subscribe(probe.ref))
  }

}
