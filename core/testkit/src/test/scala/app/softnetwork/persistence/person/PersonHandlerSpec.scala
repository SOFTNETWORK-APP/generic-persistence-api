package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.model._
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.person.query.{
  PersonToExternalProcessorStream,
  PersonToJsonProcessorStream
}
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import org.slf4j.{Logger, LoggerFactory}

class PersonHandlerSpec extends PersonTestKit with InMemoryPersistenceTestKit {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys => {
      new PersonToJsonProcessorStream() {
        lazy val log: Logger = PersonHandlerSpec.this.log
        override val forTests: Boolean = true

        override protected val manifestWrapper: ManifestW = ManifestW()

        override implicit def system: ActorSystem[_] = sys

        override def reader: StateWrappertReader[Person] = new StateWrappertReader[Person] {
          override protected val manifestWrapper: ManifestW = ManifestW()
        }
      }
    }
}
