package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.JdbcOffsetProvider
import app.softnetwork.persistence.jdbc.scalatest.JdbcPersistenceTestKit
import app.softnetwork.persistence.jdbc.schema.JdbcSchemaProvider
import app.softnetwork.persistence.person.query.{
  PersonToExternalProcessorStream,
  PersonToInMemoryProcessorStreamWithJdbcJournal
}
import com.typesafe.config.Config

trait JdbcPersonTestKit extends PersonTestKit with JdbcPersistenceTestKit { _: JdbcSchemaProvider =>

  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys =>
      new PersonToInMemoryProcessorStreamWithJdbcJournal with JdbcOffsetProvider {
        override def config: Config = JdbcPersonTestKit.this.config

        override val forTests: Boolean = true
        override protected val manifestWrapper: ManifestW = ManifestW()
        override implicit def system: ActorSystem[_] = sys
      }
}
