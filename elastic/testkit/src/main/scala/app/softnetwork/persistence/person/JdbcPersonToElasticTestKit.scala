package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcOffsetProvider}
import app.softnetwork.persistence.person.query.{
  PersonToElasticProcessorStream,
  PersonToExternalProcessorStream
}
import com.typesafe.config.Config

trait JdbcPersonToElasticTestKit extends PersonToElasticTestKit with JdbcPersonTestKit {

  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys => {
      new PersonToElasticProcessorStream with JdbcJournalProvider with JdbcOffsetProvider {
        override val forTests: Boolean = true
        override implicit def system: ActorSystem[_] = sys

        override def config: Config = JdbcPersonToElasticTestKit.this.config.withFallback(
          JdbcPersonToElasticTestKit.this.elasticConfig
        )
      }
    }
}
