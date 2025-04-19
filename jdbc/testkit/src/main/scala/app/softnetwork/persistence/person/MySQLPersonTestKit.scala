package app.softnetwork.persistence.person

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.JdbcOffsetProvider
import app.softnetwork.persistence.jdbc.scalatest.MySQLTestKit
import app.softnetwork.persistence.model.StateWrappertReader
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.person.query.{
  PersonToExternalProcessorStream,
  PersonToJdbcProcessorStreamWithJdbcJournal
}
import app.softnetwork.persistence.query.ExternalPersistenceProvider
import com.typesafe.config.Config
import slick.jdbc.MySQLProfile

trait MySQLPersonTestKit extends JdbcPersonTestKit with MySQLTestKit with MySQLProfile {
  override def externalPersistenceProvider: ExternalPersistenceProvider[Person] = this
  override def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream =
    sys =>
      new PersonToJdbcProcessorStreamWithJdbcJournal with JdbcOffsetProvider with MySQLProfile {
        override def config: Config = MySQLPersonTestKit.this.config

        override val forTests: Boolean = true
        override protected val manifestWrapper: ManifestW = ManifestW()
        override implicit def system: ActorSystem[_] = sys
        override def reader: StateWrappertReader[Person] = new StateWrappertReader[Person] {
          override protected val manifestWrapper: ManifestW = ManifestW()
        }
      }
}
