package app.softnetwork.resource.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.query.ExternalPersistenceProvider
import app.softnetwork.resource.model.GenericResource
import app.softnetwork.resource.spi.{LocalFileSystemProvider, ResourceProvider}

import scala.reflect.ClassTag

protected[resource] trait GenericResourceProvider[Resource <: GenericResource] extends ExternalPersistenceProvider[Resource] {
  _: ResourceProvider with ManifestWrapper[Resource] =>

  /**
    * Creates the underlying document to the external system
    *
    * @param document - the document to create
    * @param t        - implicit ClassTag for T
    * @return whether the operation is successful or not
    */
  override def createDocument(document: Resource)(implicit t: ClassTag[Resource]): Boolean = {
    import document._
    upsertDocument(uuid, content)
  }

  /**
    * Updates the underlying document to the external system
    *
    * @param document - the document to update
    * @param upsert   - whether or not to create the underlying document if it does not exist in the external system
    * @param t        - implicit ClassTag for T
    * @return whether the operation is successful or not
    */
  override def updateDocument(document: Resource, upsert: Boolean = true)(implicit t: ClassTag[Resource]): Boolean = {
    import document._
    upsertDocument(uuid, content)
  }

  /**
    * Upsert the underlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to upsert
    * @param data - the document data base64 encoded
    * @return whether the operation is successful or not
    */
  override def upsertDocument(uuid: String, data: String): Boolean = {
    upsertResource(uuid, data)
  }

  /**
    * Deletes the underlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to delete
    * @return whether the operation is successful or not
    */
  override def deleteDocument(uuid: String): Boolean = {
    deleteResource(uuid)
  }

}

trait GenericLocalFileSystemResourceProvider[Resource <: GenericResource] extends GenericResourceProvider[Resource]
  with LocalFileSystemProvider { _: ManifestWrapper[Resource] =>
}