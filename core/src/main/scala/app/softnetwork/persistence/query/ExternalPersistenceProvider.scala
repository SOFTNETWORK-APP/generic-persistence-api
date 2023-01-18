package app.softnetwork.persistence.query

import org.json4s.Formats
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.model.Timestamped

import scala.reflect.ClassTag

/** Created by smanciot on 16/05/2020.
  */
trait ExternalPersistenceProvider[T <: Timestamped] { _: ManifestWrapper[T] =>

  /** Creates the unerlying document to the external system
    *
    * @param document
    *   - the document to create
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  def createDocument(document: T)(implicit t: ClassTag[T] = manifestWrapper.wrapped): Boolean =
    false

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
  def updateDocument(document: T, upsert: Boolean = true)(implicit
    t: ClassTag[T] = manifestWrapper.wrapped
  ): Boolean = false

  /** Upserts the unerlying document referenced by its uuid to the external system
    *
    * @param uuid
    *   - the uuid of the document to upsert
    * @param data
    *   - the document data
    * @return
    *   whether the operation is successful or not
    */
  def upsertDocument(uuid: String, data: String): Boolean = false

  /** Deletes the unerlying document referenced by its uuid to the external system
    *
    * @param uuid
    *   - the uuid of the document to delete
    * @return
    *   whether the operation is successful or not
    */
  def deleteDocument(uuid: String): Boolean = false

  /** Load the document referenced by its uuid
    *
    * @param uuid
    *   - the document uuid
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the document retrieved, None otherwise
    */
  def loadDocument(
    uuid: String
  )(implicit m: Manifest[T] = manifestWrapper.wrapped, formats: Formats): Option[T] = None

  /** Search documents
    *
    * @param query
    *   - the search query
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the documents founds or an empty list otherwise
    */
  def searchDocuments(
    query: String
  )(implicit m: Manifest[T] = manifestWrapper.wrapped, formats: Formats): List[T] = List.empty

}
