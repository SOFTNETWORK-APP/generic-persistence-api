package app.softnetwork.resource.spi

import app.softnetwork.utils.ImageTools.ImageSize

import java.nio.file.Path

protected[resource] trait ResourceProvider {

  /**
    * Upsert the underlying resource referenced by its uuid to the resource provider
    *
    * @param uuid - the uuid of the resource to upsert
    * @param data - the base64 encoded resource content
    * @param uri - the optional uri of the resource
    * @return whether the resource has been upserted or not
    */
  def upsertResource(uuid: String, data: String, uri: Option[String] = None): Boolean

  /**
    *
    * @param uuid - the uuid of the resource to load
    * @param uri - the optional uri of the resource
    * @param content - the optional base64 encoded resource content
    * @param option - the list of resource options
    * @return the optional path associated with this resource
    */
  def loadResource(uuid: String, uri: Option[String], content: Option[String], option: ResourceOption*): Option[Path]

  /**
    *
    * @param uri - the uri from which resources have to be listed
    * @return the resources located at this uri
    */
  def listResources(uri: String): List[SimpleResource] = List.empty

  /**
    * Deletes the underlying resource referenced by its uuid to the resource provider
    *
    * @param uuid - the uuid of the resource to delete
    * @param uri - the optional uri of the resource
    * @return whether the operation is successful or not
    */
  def deleteResource(uuid: String, uri: Option[String] = None): Boolean
}

protected[resource] trait ResourceOption

case class SizeOption(size: ImageSize) extends ResourceOption

case class SimpleResource(uri: String, name: String, directory: Boolean, image: Boolean)
