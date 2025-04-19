package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.jdbc.query.JdbcStateProvider
import app.softnetwork.persistence.jdbc.scalatest.JdbcPersistenceTestKit
import app.softnetwork.persistence.model.StateWrappertReader
import app.softnetwork.persistence.person.model.Person
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

trait JdbcPersonProvider extends JdbcStateProvider[Person] with ManifestWrapper[Person] {
  _: JdbcPersistenceTestKit with JdbcProfile =>
  override protected val manifestWrapper: ManifestW = ManifestW()

  override def reader: StateWrappertReader[Person] = new StateWrappertReader[Person] {
    override protected val manifestWrapper: ManifestW = ManifestW()
  }

  override implicit def executionContext: ExecutionContext = system.executionContext

}
