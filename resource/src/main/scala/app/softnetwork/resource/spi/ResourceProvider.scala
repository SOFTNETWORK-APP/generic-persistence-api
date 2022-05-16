package app.softnetwork.resource.spi

import app.softnetwork.utils.ImageTools.ImageSize

import java.nio.file.Path

protected[resource] trait ResourceProvider {

  /**
    * Upsert the underlying resource referenced by its uuid to the resource provider
    *
    * @param uuid - the uuid of the resource to upsert
    * @param data - the base64 encoded resource content
    * @return whether the resource has been upserted or not
    */
  def upsertResource(uuid: String, data: String): Boolean

  /**
    *
    * @param uuid - the uuid of the resource to load
    * @param content - the optional base64 encoded resource content
    * @param option - the list of resource options
    * @return the optional path associated with this resource
    */
  def loadResource(uuid: String, content: Option[String], option: ResourceOption*): Option[Path]

}

protected[resource] trait ResourceOption

case class SizeOption(size: ImageSize) extends ResourceOption
