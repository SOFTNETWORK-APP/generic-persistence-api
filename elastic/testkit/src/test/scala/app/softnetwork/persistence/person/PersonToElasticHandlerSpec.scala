package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.elastic.scalatest.EmbeddedElasticTestKit
import app.softnetwork.persistence.person.query.{
  PersonToElasticProcessorStream,
  PersonToExternalProcessorStream
}
import app.softnetwork.persistence.query.{InMemoryJournalProvider, InMemoryOffsetProvider}
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

class PersonToElasticHandlerSpec
    extends PersonToElasticTestKit
    with InMemoryPersistenceTestKit
    with EmbeddedElasticTestKit {

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys => {
      new PersonToElasticProcessorStream with InMemoryJournalProvider with InMemoryOffsetProvider {
        lazy val log: Logger = LoggerFactory getLogger getClass.getName
        override def config: Config = PersonToElasticHandlerSpec.this.elasticConfig

        override val forTests: Boolean = true
        override implicit def system: ActorSystem[_] = sys
      }
    }
}
