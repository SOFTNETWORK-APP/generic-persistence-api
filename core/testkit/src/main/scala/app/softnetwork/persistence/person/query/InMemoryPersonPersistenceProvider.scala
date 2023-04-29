package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.query.InMemoryPersistenceProvider
import org.json4s.Formats

trait InMemoryPersonPersistenceProvider
    extends InMemoryPersistenceProvider[Person]
    with ManifestWrapper[Person] {

  import InMemoryPersonPersistenceProvider._

  override def addObject(o: Person): Map[String, Person] = {
    persons = persons.updated(o.uuid, o)
    persons
  }

  override def removeObject(uuid: String): Map[String, Person] = {
    persons = persons - uuid
    persons
  }

  /** Load the document referenced by its uuid
    *
    * @param uuid
    *   - the document uuid
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the document retrieved, None otherwise
    */
  override def loadDocument(
    uuid: String
  )(implicit m: Manifest[Person], formats: Formats): Option[Person] = {
    persons.get(uuid)
  }

  /** Search documents
    *
    * @param query
    *   - the search query
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the documents founds or an empty list otherwise
    */
  override def searchDocuments(
    query: String
  )(implicit m: Manifest[Person], formats: Formats): List[Person] = {
    persons.values.toList
  }
}

object InMemoryPersonPersistenceProvider extends InMemoryPersonPersistenceProvider {
  override protected val manifestWrapper: ManifestW = ManifestW()
  var persons: Map[String, Person] = Map.empty
}
