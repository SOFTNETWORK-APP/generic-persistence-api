package app.softnetwork.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.serialization._
import org.json4s.Formats
import org.json4s.jackson.JsonMethods._

import scala.reflect.ClassTag

trait InMemoryPersistenceProvider[T <: Timestamped] extends ExternalPersistenceProvider[T] {
  _: ManifestWrapper[T] =>

  implicit def formats: Formats = commonFormats

  implicit def excludedFields: List[String] = defaultExcludedFields :+ "data"

  def addObject(o: T): Map[String, T] = objects.updated(o.uuid, o)

  def removeObject(uuid: String): Map[String, T] = objects - uuid

  private[this] var objects: Map[String, T] = Map.empty

  /** Creates the unerlying document to the external system
    *
    * @param document
    *   - the document to create
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  override def createDocument(document: T)(implicit t: ClassTag[T]): Boolean = {
    objects = addObject(document)
    true
  }

  /** Updates the unerlying document to the external system
    *
    * @param document
    *   - the document to update
    * @param upsert
    *   - whether or not to create the underlying document if it does not exist in the external
    *     system
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  override def updateDocument(document: T, upsert: Boolean)(implicit t: ClassTag[T]): Boolean = {
    objects = addObject(document)
    true
  }

  /** Upserts the unerlying document referenced by its uuid to the external system
    *
    * @param uuid
    *   - the uuid of the document to upsert
    * @param data
    *   - the document data
    * @return
    *   whether the operation is successful or not
    */
  override def upsertDocument(uuid: String, data: String): Boolean = {
    objects.get(uuid) match {
      case Some(person) =>
        val output: String = caseClass2Map(person) ++ parse(data).extract[Map[String, Any]]
        val o: T = serialization.read(output)(formats, manifestWrapper.wrapped)
        objects = addObject(o)
        true
      case _ =>
        false
    }
  }

  /** Deletes the unerlying document referenced by its uuid to the external system
    *
    * @param uuid
    *   - the uuid of the document to delete
    * @return
    *   whether the operation is successful or not
    */
  override def deleteDocument(uuid: String): Boolean = {
    objects = removeObject(uuid)
    true
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
  override def loadDocument(uuid: String)(implicit m: Manifest[T], formats: Formats): Option[T] = {
    objects.get(uuid)
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
  )(implicit m: Manifest[T], formats: Formats): List[T] = {
    objects.values.toList
  }
}
