package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.person.query.{
  PersonToExternalProcessorStream,
  PersonToInMemoryProcessorStream
}
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import org.slf4j.{Logger, LoggerFactory}

class PersonServiceSpec extends PersonServerTestKit with InMemoryPersistenceTestKit {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys => {
      new PersonToInMemoryProcessorStream() {
        lazy val log: Logger = PersonServiceSpec.this.log
        override val forTests: Boolean = true

        override protected val manifestWrapper: ManifestW = ManifestW()

        override implicit def system: ActorSystem[_] = sys
      }
    }
}
