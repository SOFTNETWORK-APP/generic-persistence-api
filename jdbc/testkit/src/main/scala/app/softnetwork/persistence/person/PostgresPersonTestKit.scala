package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.scalatest.PostgresTestKit
import app.softnetwork.persistence.model.StateWrappertReader
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.person.query.{
  PersonToExternalProcessorStream,
  PersonToJdbcProcessorStreamWithJdbcJournal
}
import app.softnetwork.persistence.query.ExternalPersistenceProvider
import com.typesafe.config.Config
import slick.jdbc.PostgresProfile

trait PostgresPersonTestKit extends JdbcPersonTestKit with PostgresTestKit with PostgresProfile {
  override def externalPersistenceProvider: ExternalPersistenceProvider[Person] = this
  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys =>
      new PersonToJdbcProcessorStreamWithJdbcJournal with PostgresProfile {
        override def config: Config = PostgresPersonTestKit.this.config

        override val forTests: Boolean = true
        override protected val manifestWrapper: ManifestW = ManifestW()
        override implicit def system: ActorSystem[_] = sys
        override def reader: StateWrappertReader[Person] = new StateWrappertReader[Person] {
          override protected val manifestWrapper: ManifestW = ManifestW()
        }
      }
}
