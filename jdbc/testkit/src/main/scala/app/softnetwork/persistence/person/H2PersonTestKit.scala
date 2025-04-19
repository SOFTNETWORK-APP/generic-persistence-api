package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.JdbcOffsetProvider
import app.softnetwork.persistence.jdbc.scalatest.H2TestKit
import app.softnetwork.persistence.model.StateWrappertReader
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.person.query.{
  PersonToExternalProcessorStream,
  PersonToJdbcProcessorStreamWithJdbcJournal
}
import app.softnetwork.persistence.query.ExternalPersistenceProvider
import com.typesafe.config.Config
import slick.jdbc.H2Profile

trait H2PersonTestKit extends JdbcPersonTestKit with H2TestKit with H2Profile {
  override def externalPersistenceProvider: ExternalPersistenceProvider[Person] = this
  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys =>
      new PersonToJdbcProcessorStreamWithJdbcJournal with JdbcOffsetProvider with H2Profile {
        override def config: Config = H2PersonTestKit.this.config

        override val forTests: Boolean = true
        override protected val manifestWrapper: ManifestW = ManifestW()
        override implicit def system: ActorSystem[_] = sys
        override def reader: StateWrappertReader[Person] = new StateWrappertReader[Person] {
          override protected val manifestWrapper: ManifestW = ManifestW()
        }
      }
}
