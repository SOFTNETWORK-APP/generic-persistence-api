package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.model.StateWrappertReader
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.query.JsonProvider

object JsonPersonProvider extends JsonProvider[Person] with ManifestWrapper[Person] {
  override protected val manifestWrapper: ManifestW = ManifestW()

  override def reader: StateWrappertReader[Person] = new StateWrappertReader[Person] {
    override protected val manifestWrapper: ManifestW = ManifestW()
  }
}
